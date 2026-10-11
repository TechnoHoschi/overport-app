package moe.crx.overport.commands

import com.reandroid.apk.ApkModule
import moe.crx.overport.cli.CliCommand
import moe.crx.overport.utils.Adb
import moe.crx.overport.utils.AdbException
import java.io.File

/**
 * Installs a patched APK on a headset: optional uninstall, install with runtime permissions,
 * OBB upload, "all files access" for media players and overport's verbose logging switch.
 */
object DeployApplicationCommand : CliCommand() {
    override fun name() = "deploy"
    override fun description() = "Install a patched APK (+ OBB, permissions) on a connected device."

    override fun printHelp(): Boolean {
        println("usage: overport deploy --apk=<path> [--obb=<file or dir>] [--device=<serial>]")
        println("    [--uninstall] [--verbose-logging]")
        println()
        println("--apk=<path>        patched APK to install")
        println("--obb=<path>        OBB file, or directory containing *.obb files, pushed to /sdcard/Android/obb/<package>/")
        println("--device=<serial>   adb serial, needed when more than one device is connected (see \"adb devices\")")
        println("--uninstall         uninstall first. Needed when the installed app has a different signature.")
        println("                    WARNING: deletes the app's data and OBB files on the device.")
        println("--verbose-logging   enable overport verbose logging (debug.sys.ovrport.verboselogging=1)")
        return true
    }

    private const val MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"

    override suspend fun execute(args: List<String>): Boolean {
        val apk = Adb.argument(args, "apk")?.let { File(it) } ?: return false
        if (!apk.isFile) {
            println("APK not found: ${apk.absolutePath}")
            return true
        }

        val module = ApkModule.loadApkFile(apk)
        val packageName = module.androidManifest.packageName
        val permissions = module.androidManifest.usesPermissions
        module.close()

        val adb = Adb(Adb.parseDeviceArgument(args))

        return try {
            if (Adb.flag(args, "uninstall")) {
                println("Uninstalling $packageName (app data and OBB on the device are deleted)...")
                adb.run("uninstall", packageName)
            }

            println("Installing ${apk.name}...")
            val install = adb.run("install", "-r", "-g", apk.absolutePath)
            if (!install.ok || !install.output.contains("Success")) {
                println(install.output)
                if (install.output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) {
                    println()
                    println("The installed app is signed with another key (e.g. the original or another patch run).")
                    println("Back up saves/OBB if needed and rerun with --uninstall.")
                }
                return true
            }

            Adb.argument(args, "obb")?.let { File(it) }?.let { obbSource ->
                val obbFiles = if (obbSource.isDirectory) {
                    obbSource.listFiles { file -> file.name.endsWith(".obb") }?.toList() ?: listOf()
                } else {
                    listOf(obbSource)
                }
                val target = "/sdcard/Android/obb/$packageName"
                adb.runOrThrow("shell", "mkdir", "-p", target)
                obbFiles.forEach { obb ->
                    println("Pushing ${obb.name}...")
                    adb.runOrThrow("push", obb.absolutePath, "$target/${obb.name}", timeoutSeconds = 3600)
                }
            }

            if (MANAGE_EXTERNAL_STORAGE in permissions) {
                println("Granting all files access...")
                adb.run("shell", "appops", "set", packageName, "MANAGE_EXTERNAL_STORAGE", "allow")
            }

            if (Adb.flag(args, "verbose-logging")) {
                adb.run("shell", "setprop", "debug.sys.ovrport.verboselogging", "1")
            }

            println("Deployed $packageName.")
            true
        } catch (e: AdbException) {
            println(e.message)
            true
        }
    }
}
