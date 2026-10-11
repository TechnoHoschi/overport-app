package moe.crx.overport.commands

import kotlinx.coroutines.delay
import moe.crx.overport.cli.CliCommand
import moe.crx.overport.utils.Adb
import moe.crx.overport.utils.AdbException
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Starts an app on a headset, records logcat for a while and summarizes crashes and XR/overport errors.
 * Writes the full log plus a short report, so a human or an AI assistant can work from the files.
 */
object TestApplicationCommand : CliCommand() {
    override fun name() = "test"
    override fun description() = "Launch an app, record logcat and summarize crashes."

    override fun printHelp(): Boolean {
        println("usage: overport test --package=<name> [--duration=<seconds>] [--output=<dir>] [--device=<serial>]")
        println()
        println("--package=<name>      package to launch")
        println("--duration=<seconds>  how long to record (default 60). Use the app on the headset meanwhile.")
        println("--output=<dir>        log directory (default: ./logs)")
        println("--device=<serial>     adb serial, needed when more than one device is connected")
        return true
    }

    private data class Finding(val label: String, val pattern: Regex)

    private val FINDINGS = listOf(
        Finding("Java crash", Regex("FATAL EXCEPTION")),
        Finding("Native crash", Regex("Fatal signal|SIGSEGV|SIGABRT|backtrace:")),
        Finding("ANR", Regex("ANR in ")),
        Finding("Missing class/method", Regex("ClassNotFoundException|NoSuchMethodError|UnsatisfiedLinkError")),
        Finding("Missing library", Regex("dlopen failed|library \\S+ not found")),
        Finding("EGL error", Regex("EGL_BAD_\\w+")),
        Finding("OpenXR error", Regex("XR_ERROR_\\w+")),
        Finding("Permission denied", Regex("Permission denied|SecurityException")),
        Finding("Platform SDK / IAP", Regex("(?i)ovr_IAP|ovr_Entitlement|GetViewerPurchases|\\bsku\\b")),
        Finding("overport", Regex("(?i)overport|ovrport")),
    )

    override suspend fun execute(args: List<String>): Boolean {
        val packageName = Adb.argument(args, "package") ?: return false
        val duration = Adb.argument(args, "duration")?.toLongOrNull() ?: 60
        val outputDir = File(Adb.argument(args, "output") ?: "logs").apply { mkdirs() }
        val adb = Adb(Adb.parseDeviceArgument(args))

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val logFile = outputDir.resolve("$packageName-$stamp.log")
        val reportFile = outputDir.resolve("$packageName-$stamp.report.txt")

        return try {
            val component = adb.runOrThrow("shell", "cmd", "package", "resolve-activity", "--brief", packageName)
                .lines().lastOrNull { it.contains('/') }?.trim()
                ?: run {
                    println("No launchable activity found for $packageName. Is it installed?")
                    return true
                }

            adb.run("shell", "am", "force-stop", packageName)
            adb.runOrThrow("logcat", "-c")
            val logcat = adb.startLogcat(logFile)

            println("Starting $component, recording for ${duration}s...")
            adb.runOrThrow("shell", "am", "start", "-n", component)

            var pid: String? = null
            var died = false
            val end = System.currentTimeMillis() + duration * 1000
            while (System.currentTimeMillis() < end) {
                delay(1000)
                val current = adb.shell("pidof $packageName").output.trim().takeIf { it.isNotEmpty() }
                if (current != null) pid = current
                if (pid != null && current == null) {
                    died = true
                    delay(3000) // let debuggerd/tombstoned finish writing
                    break
                }
            }

            logcat.destroy()
            logcat.waitFor()

            val lines = logFile.readLines()
            val report = buildString {
                appendLine("Package:  $packageName ($component)")
                appendLine("Device:   ${adb.shell("getprop ro.product.model").output} / Android ${adb.shell("getprop ro.build.version.release").output}")
                appendLine("PID:      ${pid ?: "never started"}")
                appendLine("Result:   ${if (pid == null) "DID NOT START" else if (died) "PROCESS DIED" else "still running after ${duration}s"}")
                appendLine("Log:      ${logFile.absolutePath} (${lines.size} lines)")
                appendLine()
                FINDINGS.forEach { finding ->
                    val hits = lines.filter { finding.pattern.containsMatchIn(it) }
                    if (hits.isNotEmpty()) {
                        appendLine("== ${finding.label}: ${hits.size} line(s)")
                        hits.take(8).forEach { appendLine("  $it") }
                        appendLine()
                    }
                }
            }

            reportFile.writeText(report)
            println()
            print(report)
            println("Report: ${reportFile.absolutePath}")
            true
        } catch (e: AdbException) {
            println(e.message)
            true
        }
    }
}
