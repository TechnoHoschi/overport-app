package moe.crx.overport.commands

import com.reandroid.apk.ApkBundle
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import moe.crx.overport.cli.CliCommand
import moe.crx.overport.utils.*
import java.io.File

private val SPLIT_ATTRIBUTES = setOf("isSplitRequired", "requiredSplitTypes", "splitTypes")
private val SPLIT_METADATA = setOf("com.android.vending.splits.required", "com.android.vending.splits")

/** Removes split markers from a merged manifest, otherwise the installer asks for the missing splits. */
private fun JSONObject?.stripSplitMarkers(): JSONObject? {
    takeAttributesEach({ elem<String>("name") in SPLIT_ATTRIBUTES }) { null }
    take<JSONArray>("nodes") {
        takeEach<JSONObject>({ elem<String>("name") == "meta-data" && nameAttribute() in SPLIT_METADATA }) { null }
        takeEach<JSONObject> { stripSplitMarkers() }
    }
    return this
}

/**
 * Pulls an installed app (base + split APKs) and its OBB files from a connected headset,
 * e.g. a game or player you bought on your Quest.
 */
object ExportApplicationCommand : CliCommand() {
    override fun name() = "export"
    override fun description() = "Pull an installed app (APKs + OBB) from a connected device."

    override fun printHelp(): Boolean {
        println("usage: overport export --package=<name> [--output=<dir>] [--device=<serial>]")
        println("       overport export --list [--device=<serial>]")
        println()
        println("--list              list installed third-party packages")
        println("--package=<name>    package to export")
        println("--output=<dir>      target directory (default: current directory); files go to <dir>/<package>/")
        println("--device=<serial>   adb serial, needed when more than one device is connected (see \"adb devices\")")
        return true
    }

    override suspend fun execute(args: List<String>): Boolean {
        val adb = Adb(Adb.parseDeviceArgument(args))

        return try {
            if (Adb.flag(args, "list")) {
                adb.runOrThrow("shell", "pm", "list", "packages", "-3")
                    .lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.sorted()
                    .forEach { println(it) }
                return true
            }

            val packageName = Adb.argument(args, "package") ?: return false
            val outputDir = File(Adb.argument(args, "output") ?: ".").resolve(packageName).apply { mkdirs() }

            val apkPaths = adb.runOrThrow("shell", "pm", "path", packageName)
                .lines().map { it.removePrefix("package:").trim() }.filter { it.endsWith(".apk") }

            if (apkPaths.isEmpty()) {
                println("Package $packageName is not installed on the device.")
                return true
            }

            val splitsDir = outputDir.resolve("splits").apply { mkdirs() }
            apkPaths.forEach { path ->
                println("Pulling ${path.substringAfterLast('/')}...")
                adb.runOrThrow("pull", path, splitsDir.resolve(path.substringAfterLast('/')).absolutePath)
            }

            val mergedApk = outputDir.resolve("$packageName.apk")
            if (apkPaths.size == 1) {
                splitsDir.listFiles()?.first()?.copyTo(mergedApk, true)
            } else {
                println("Merging ${apkPaths.size} split APKs...")
                ApkBundle().use { bundle ->
                    bundle.loadApkDirectory(splitsDir)
                    bundle.mergeModules().apply {
                        val manifestJson = androidManifest.toJson().stripSplitMarkers()
                        androidManifest.fromJson(manifestJson)
                        androidManifest.refresh()
                        writeApk(mergedApk)
                        close()
                    }
                }
            }

            val obbDir = "/sdcard/Android/obb/$packageName"
            val obbFiles = adb.shell("ls $obbDir").takeIf { it.ok }?.output
                ?.lines()?.map { it.trim() }?.filter { it.endsWith(".obb") } ?: listOf()

            obbFiles.forEach { obb ->
                println("Pulling $obb...")
                adb.runOrThrow("pull", "$obbDir/$obb", outputDir.resolve(obb).absolutePath, timeoutSeconds = 3600)
            }

            println()
            println("Exported ${apkPaths.size} APK(s) and ${obbFiles.size} OBB file(s) to ${outputDir.absolutePath}")
            println("Patch this file: ${mergedApk.absolutePath}")
            true
        } catch (e: AdbException) {
            println(e.message)
            true
        }
    }
}
