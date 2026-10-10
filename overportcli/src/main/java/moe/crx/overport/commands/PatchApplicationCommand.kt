package moe.crx.overport.commands

import kotlinx.coroutines.cancelAndJoin
import moe.crx.overport.cli.CliCommand
import moe.crx.overport.patches.PLATFORM_ICON_RESIZER
import moe.crx.overport.patching.InvalidPatchArgumentException
import moe.crx.overport.patching.PatchStore
import moe.crx.overport.patching.PatcherContext
import moe.crx.overport.utils.DesktopUtil.defaultWorkspace
import moe.crx.overport.utils.ImageIOIconResizer
import moe.crx.overport.utils.NameFormatter.DEFAULT_FORMAT
import moe.crx.overport.utils.PrintUtil.drawProgressBar
import moe.crx.overport.utils.PrintUtil.drawSpinner
import moe.crx.overport.utils.PrintUtil.updateLine
import moe.crx.overport.versions.VersionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PatchApplicationCommand : CliCommand() {
    override fun name() = "patch"
    override fun description() = "Patch provided APK file."

    override fun printHelp(): Boolean {
        println("usage: overport patch --input=<path> [--output=<path>]")
        println("    [--output-name=<format>] [--workspace=<path>] [--patches=<names>] [--extra-patches=<names>]")
        println("    [--version=<value>]")
        println()
        println("--input=<path>")
        println("Specifies input APK file to patch.")
        println()
        println("--output=<path>")
        println("Specifies output APK file directory path.")
        println("If not specified, uses same path as input file.")
        println("If specified path is a file, uses it's parent directory.")
        println()
        println("--output-name=<format>")
        println("Specifies output APK file name format string.")
        println("If not specified, uses name of input file but with \"output\" suffix appended to it.")
        println("See more information about name format below.")
        println()
        println("--workspace=<path>")
        println("Specifies workspace directory path.")
        println("If not specified, uses default one in user's home directory.")
        println()
        println("--patches=<value>")
        println("Specifies patches to apply. Value should be a semicolon-separated list of patch names.")
        println("If not specified, uses every recommended patch available. Use \"overport patches\" to see available patches.")
        println("You can also specify arguments for a specific patch using <patch name>=<comma-separated arguments>.")
        println()
        println("--extra-patches=<value>")
        println("Same syntax as --patches. Adds patches (or arguments) on top of --patches or the recommended set.")
        println("Example: --extra-patches=\"patch_platform_products=premium_sku;patch_runtime_config=verbose_logging=1\"")
        println()
        println("--version=<value>")
        println("Specifies which overport version to use for patching.")
        println("Value also could be \"experimental\" or \"latest\".")
        println("If not specified, uses latest one. Use \"overport install\" to see available versions.")
        println()
        println("About file name format:")
        println("File name format allows to specify pattern for automatic file name generation.")
        println("Default file name format is $DEFAULT_FORMAT.")
        println("Available format tags:")
        println("{package} - application package name")
        println("{label} - application label")
        println("{code} - application version code")
        println("{version} - application version name")
        println("{overport} - overport version used for patching")
        println("{filename} - input file name without extension")
        println("{ext} - input file extension")
        println("{filenameext} - input file name with extension")
        return true
    }

    private data class ParsedArguments(
        var inputFile: String? = null,
        var outputFile: String? = null,
        var workspace: String? = null,
        var patches: Map<String, List<String>>? = null,
        var extraPatches: Map<String, List<String>> = mapOf(),
        var nameFormat: String? = null,
        var overportVersion: String? = null,
    )

    private fun scanArguments(args: List<String>): ParsedArguments {
        val parsed = ParsedArguments()

        val associated = args.associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }

        associated.forEach { (key, value) ->
            when (key) {
                "--input" -> parsed.inputFile = value
                "--output" -> parsed.outputFile = value
                "--workspace" -> parsed.workspace = value
                "--patches" -> parsed.patches = parsePatchList(value)
                "--extra-patches" -> parsed.extraPatches = parsePatchList(value)

                "--output-name" -> parsed.nameFormat = value
                "--version" -> parsed.overportVersion = value
            }
        }

        return parsed
    }

    private fun parsePatchList(value: String): Map<String, List<String>> {
        return value.split(';').map { it.trim() }.filter { it.isNotEmpty() }.associate {
            it.substringBefore('=') to it.substringAfter('=', "").split(',').map { splitted -> splitted.trim() }
                .filter { splitted -> splitted.isNotEmpty() }
        }
    }

    override suspend fun execute(args: List<String>): Boolean {
        val parsed = scanArguments(args)

        val inputFile = parsed.inputFile?.let { File(it) } ?: return false

        if (!inputFile.isFile) {
            return false
        }

        var outputDir = parsed.outputFile?.let { File(it) } ?: inputFile.parentFile

        if (outputDir.isFile) {
            outputDir = outputDir.parentFile
        }

        val workspace = parsed.workspace?.let { File(it) } ?: defaultWorkspace()

        val patches = (parsed.patches ?: PatchStore.recommended().associate { it.name to listOf() }) + parsed.extraPatches

        val unknownPatches = patches.keys.filter { PatchStore.get(it) == null }
        if (unknownPatches.isNotEmpty()) {
            println("Unknown patch(es): ${unknownPatches.joinToString()}. Use \"overport patches\" to list available patches.")
            return true
        }

        val nameFormat = parsed.nameFormat ?: DEFAULT_FORMAT

        val version = parsed.overportVersion ?: "latest"

        println("overport cli utility, version ${VersionManager.VERSION}")
        println("Input APK: ${inputFile.absolutePath}")
        println("Output directory: ${outputDir.absolutePath}")
        println("Workspace directory: ${workspace.absolutePath}")

        PLATFORM_ICON_RESIZER = ImageIOIconResizer
        val patcher = PatcherContext(version, workspace, inputFile.name, nameFormat)

        val waitingForLibraries = updateLine {
            drawSpinner()
            print("Preparing libraries...")
        }
        patcher.checkout()
        waitingForLibraries.cancelAndJoin()

        println("Using overport version: ${patcher.overportVersion()}")

        val waitingForDecompilation = updateLine {
            drawSpinner()
            print("Decompiling APK...")
        }
        FileInputStream(inputFile).use { fis ->
            patcher.prepare(fis)
        }
        waitingForDecompilation.cancelAndJoin()

        println("Decompiled app: ${patcher.appName()}, package ${patcher.appPackage()}, version ${patcher.appVersionName()}")

        val totalPatches = patches.size
        var appliedPatches = 0
        var currentPatch = ""
        val waitingForPatching = updateLine {
            drawSpinner()
            drawProgressBar(appliedPatches.toLong(), totalPatches.toLong())
            print("Applying patch $currentPatch... ")
            print("($appliedPatches / $totalPatches)")
        }
        try {
            patcher.patch(patches) { index, patch ->
                appliedPatches = index
                currentPatch = patch.name
            }
        } catch (e: InvalidPatchArgumentException) {
            waitingForPatching.cancelAndJoin()
            println()
            println("Invalid patch argument: ${e.message}")
            patcher.cleanup()
            return true
        }
        waitingForPatching.cancelAndJoin()

        outputDir.mkdirs()

        val outputFile = File(outputDir, patcher.outputFileName())

        println("Output file: ${outputFile.absolutePath}")

        val waitingForCompilation = updateLine {
            drawSpinner()
            print("Compiling APK... ")
        }
        FileOutputStream(outputFile).use { fos ->
            patcher.export(fos)
        }
        waitingForCompilation.cancelAndJoin()

        val waitingForCleaning = updateLine {
            drawSpinner()
            print("Cleaning up... ")
        }
        patcher.cleanup()
        waitingForCleaning.cancelAndJoin()

        println("Patching successful.")

        return true
    }
}