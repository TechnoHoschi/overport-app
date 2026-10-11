package moe.crx.overport.utils

import java.io.File
import java.util.concurrent.TimeUnit

class AdbException(message: String) : RuntimeException(message)

/**
 * Thin wrapper around the adb binary. Uses `OVERPORT_ADB` if set, otherwise `adb` from PATH.
 * Every call targets [serial] when given, so several headsets can be connected at the same time.
 */
class Adb(private val serial: String?) {
    data class Result(val exitCode: Int, val output: String) {
        val ok get() = exitCode == 0
    }

    private val executable = System.getenv("OVERPORT_ADB")?.takeIf { it.isNotBlank() } ?: "adb"

    fun command(vararg args: String): List<String> {
        return listOf(executable) + (serial?.let { listOf("-s", it) } ?: listOf()) + args
    }

    fun run(vararg args: String, timeoutSeconds: Long = 600): Result {
        val process = runCatching {
            ProcessBuilder(command(*args)).redirectErrorStream(true).start()
        }.getOrElse {
            throw AdbException("Can't start adb (\"$executable\"). Install Android platform-tools or set OVERPORT_ADB.")
        }

        val output = StringBuilder()
        val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AdbException("adb ${args.joinToString(" ")} timed out after ${timeoutSeconds}s.")
        }
        reader.join()

        return Result(process.exitValue(), output.toString().trim())
    }

    fun runOrThrow(vararg args: String, timeoutSeconds: Long = 600): String {
        val result = run(*args, timeoutSeconds = timeoutSeconds)
        if (!result.ok) {
            throw AdbException("adb ${args.joinToString(" ")} failed (${result.exitCode}): ${result.output}")
        }
        return result.output
    }

    fun shell(command: String): Result = run("shell", command)

    /** Starts `adb logcat` writing into [file]; caller stops it with [Process.destroy]. */
    fun startLogcat(file: File): Process {
        return ProcessBuilder(command("logcat", "-v", "threadtime"))
            .redirectErrorStream(true)
            .redirectOutput(file)
            .start()
    }

    companion object {
        fun parseDeviceArgument(args: List<String>): String? {
            return args.firstOrNull { it.startsWith("--device=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
        }

        fun argument(args: List<String>, name: String): String? {
            return args.firstOrNull { it.startsWith("--$name=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
        }

        fun flag(args: List<String>, name: String): Boolean = args.contains("--$name")
    }
}
