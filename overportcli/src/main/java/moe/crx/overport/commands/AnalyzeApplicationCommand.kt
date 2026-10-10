package moe.crx.overport.commands

import com.reandroid.apk.ApkModule
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import moe.crx.overport.cli.CliCommand
import moe.crx.overport.utils.elem
import moe.crx.overport.utils.elemEach
import moe.crx.overport.utils.nameAttribute
import java.io.File

/**
 * Read-only report about a Quest APK: engine, XR stack, manifest state and platform SDK (IAP/entitlement) usage.
 * Meant to decide which patches and parameters an app needs before patching it.
 */
object AnalyzeApplicationCommand : CliCommand() {
    override fun name() = "analyze"
    override fun description() = "Analyze APK file without patching it."

    override fun printHelp(): Boolean {
        println("usage: overport analyze --input=<path>")
        println()
        println("Prints engine, XR runtime libraries, Android XR manifest readiness and")
        println("Meta platform SDK usage (in-app purchases, entitlement, DLC) of the APK.")
        return true
    }

    private const val MAX_SCAN_SIZE = 96L * 1024 * 1024

    private val XR_LIBRARIES = linkedMapOf(
        "libvrapi.so" to "VrApi (legacy Meta API)",
        "libOVRPlugin.so" to "OVRPlugin (Unity/Unreal Oculus integration)",
        "libopenxr_loader.so" to "OpenXR loader (Meta build or overport dispatcher)",
        "libOculusXRPlugin.so" to "Unity Oculus XR Plugin",
        "libUnityOpenXR.so" to "Unity OpenXR plugin",
        "libovrplatformloader.so" to "Meta Platform SDK loader (IAP, entitlement, users)",
        "libMetaXRAudioUnity.so" to "Meta XR Audio (Unity)",
        "libMetaXRAudioWwise.so" to "Meta XR Audio (Wwise)",
        "liboverport.config.so" to "overport config (APK is already patched)",
    )

    private val PLATFORM_NEEDLES = linkedMapOf(
        "Entitlement check" to listOf("GetIsViewerEntitled", "Entitlement_GetIsViewerEntitled"),
        "Owned purchases (IAP)" to listOf("GetViewerPurchases"),
        "Product lookup by SKU (IAP)" to listOf("GetProductsBySKU"),
        "Checkout flow (IAP)" to listOf("LaunchCheckoutFlow"),
        "Downloadable content (AssetFile)" to listOf("AssetFile_GetList", "AssetFile_DownloadById", "AssetFile_StatusById"),
        "User / account requests" to listOf("GetLoggedInUser"),
        "Passthrough (XR_FB_passthrough)" to listOf("XR_FB_passthrough", "OVRPassthroughLayer"),
    )

    private val ANDROID_XR_FEATURES = setOf(
        "android.software.xr.api.openxr",
        "android.hardware.xr.input.controller",
        "android.hardware.xr.input.hand_tracking",
    )

    private data class ManifestElement(val tag: String, val name: String?)

    private fun collectElements(node: JSONObject?, into: MutableList<ManifestElement>) {
        node.elem<JSONArray>("nodes").elemEach<JSONObject>().reversed().forEach { child ->
            child.elem<String>("name")?.let { into.add(ManifestElement(it, child.nameAttribute())) }
            collectElements(child, into)
        }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun ByteArray.containsString(text: String): Boolean {
        return indexOf(text.toByteArray(Charsets.UTF_8)) >= 0 || indexOf(text.toByteArray(Charsets.UTF_16LE)) >= 0
    }

    override suspend fun execute(args: List<String>): Boolean {
        val input = args.firstOrNull { it.startsWith("--input=") }?.substringAfter('=')?.let { File(it) }
            ?: return false

        if (!input.isFile) {
            println("Input file not found: ${input.absolutePath}")
            return true
        }

        val apk = ApkModule.loadApkFile(input)
        val manifest = apk.androidManifest
        val entries = apk.zipEntryMap.toArray().map { it.name }

        println("== Application")
        println("Package:      ${manifest.packageName}")
        println("Version:      ${manifest.versionName} (${manifest.versionCode})")
        println("SDK:          min ${manifest.minSdkVersion}, target ${manifest.targetSdkVersion}")

        val abis = entries.filter { it.startsWith("lib/") }.map { it.split('/')[1] }.distinct().sorted()
        println("ABIs:         ${abis.ifEmpty { listOf("none (Java only)") }.joinToString()}")

        fun hasLibrary(name: String) = entries.any { it.startsWith("lib/") && it.endsWith("/$name") }

        val engine = when {
            hasLibrary("libil2cpp.so") -> "Unity (IL2CPP)"
            entries.any { it.endsWith("Managed/Assembly-CSharp.dll") } -> "Unity (Mono)"
            hasLibrary("libunity.so") -> "Unity"
            hasLibrary("libUE4.so") -> "Unreal Engine 4"
            hasLibrary("libUnreal.so") -> "Unreal Engine 5"
            hasLibrary("libgodot_android.so") -> "Godot"
            else -> "unknown / native"
        }
        println("Engine:       $engine")

        println()
        println("== XR stack")
        val foundXr = XR_LIBRARIES.filterKeys { hasLibrary(it) }
        if (foundXr.isEmpty()) println("No known XR libraries found.")
        foundXr.forEach { (library, description) -> println("- $library: $description") }
        if (hasLibrary("libvrapi.so") && !hasLibrary("libopenxr_loader.so")) {
            println("! VrApi only: no OpenXR path, porting needs a VrApi translation layer.")
        }

        val elements = mutableListOf<ManifestElement>()
        collectElements(manifest.toJson(), elements)

        println()
        println("== Manifest")
        val features = elements.filter { it.tag == "uses-feature" }.mapNotNull { it.name }
        val libraries = elements.filter { it.tag == "uses-library" || it.tag == "uses-native-library" }
        val categories = elements.filter { it.tag == "category" }.mapNotNull { it.name }.distinct()
        val properties = elements.filter { it.tag == "property" }.mapNotNull { it.name }.distinct()
        val questMetadata = elements.filter { it.tag == "meta-data" && it.name?.startsWith("com.oculus") == true }
            .mapNotNull { it.name }.distinct()

        println("Features:     ${features.ifEmpty { listOf("-") }.joinToString()}")
        println("Libraries:    ${libraries.ifEmpty { null }?.joinToString { "${it.name} <${it.tag}>" } ?: "-"}")
        println("Categories:   ${categories.ifEmpty { listOf("-") }.joinToString()}")
        println("Quest meta:   ${questMetadata.ifEmpty { listOf("-") }.joinToString()}")

        println()
        println("== Android XR readiness (before patching)")
        val checks = linkedMapOf(
            "XR start mode property" to ("android.window.PROPERTY_XR_ACTIVITY_START_MODE" in properties),
            "OpenXR feature declared" to ("android.software.xr.api.openxr" in features),
            "libopenxr.google.so as uses-native-library" to libraries.any {
                it.tag == "uses-native-library" && it.name == "libopenxr.google.so"
            },
            "Android XR input features" to features.any { it in ANDROID_XR_FEATURES },
        )
        checks.forEach { (check, ok) -> println("${if (ok) "[x]" else "[ ]"} $check") }

        println()
        println("== Meta platform SDK usage")
        val scanned = apk.zipEntryMap.toArray().filter { source ->
            val name = source.name
            (name.endsWith(".dex") || name.endsWith("global-metadata.dat") || name.endsWith(".dll")) &&
                    runCatching { source.length <= MAX_SCAN_SIZE }.getOrDefault(false)
        }.map { source -> source.name to source.openStream().use { it.readBytes() } }

        val hits = PLATFORM_NEEDLES.mapValues { (_, needles) ->
            scanned.filter { (_, bytes) -> needles.any { bytes.containsString(it) } }.map { it.first }
        }.filterValues { it.isNotEmpty() }

        if (hits.isEmpty()) {
            println("No platform SDK calls found in dex/metadata/managed assemblies.")
        }
        hits.forEach { (feature, files) -> println("- $feature (${files.joinToString()})") }

        if (hits.keys.any { it.contains("IAP") }) {
            println()
            println("This app checks in-app purchases. If you own a premium unlock, find its SKU with")
            println("  adb shell setprop debug.sys.ovrport.verboselogging 1")
            println("  adb logcat | grep -i -E \"sku|purchase|iap\"")
            println("and patch with --extra-patches=\"patch_platform_products=<sku>\".")
        }

        apk.close()
        return true
    }
}
