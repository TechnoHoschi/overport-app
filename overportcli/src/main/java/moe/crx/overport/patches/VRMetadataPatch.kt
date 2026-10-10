package moe.crx.overport.patches

import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import moe.crx.overport.patching.Patch
import moe.crx.overport.patching.choice
import moe.crx.overport.patching.keyValueArguments
import moe.crx.overport.utils.nameAttribute
import moe.crx.overport.utils.named
import moe.crx.overport.utils.takeNodes
import moe.crx.overport.utils.takeNodesEach

fun createMetadata(name: String, value: String): JSONObject {
    return JSONObject().put("node_type", "element").put("name", "meta-data").put(
        "attributes", JSONArray().put(
            JSONObject().put("name", "name").put("id", 16842755)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", name)
        ).put(
            JSONObject().put("name", "value").put("id", 16842788)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", value)
        )
    )
}

fun createMetadataFloat(name: String, value: Float): JSONObject {
    return JSONObject().put("node_type", "element").put("name", "meta-data").put(
        "attributes", JSONArray().put(
            JSONObject().put("name", "name").put("id", 16842755)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", name)
        ).put(
            JSONObject().put("name", "value").put("id", 16842788)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "FLOAT").put("data", value.toRawBits())
        )
    )
}

fun createUsesFeature(name: String, required: Boolean): JSONObject {
    return JSONObject().put("node_type", "element").put("name", "uses-feature").put(
        "attributes", JSONArray().put(
            JSONObject().put("name", "name").put("id", 16842755)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", name)
        ).put(
            JSONObject().put("name", "required").put("id", 16843406)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "BOOLEAN").put("data", required)
        )
    )
}

fun createUsesLibrary(name: String, required: Boolean): JSONObject {
    return JSONObject().put("node_type", "element").put("name", "uses-library").put(
        "attributes", JSONArray().put(
            JSONObject().put("name", "name").put("id", 16842755)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", name)
        ).put(
            JSONObject().put("name", "required").put("id", 16843406)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "BOOLEAN").put("data", required)
        )
    )
}

fun createUsesNativeLibrary(name: String, required: Boolean): JSONObject {
    return createUsesLibrary(name, required).put("name", "uses-native-library")
}

fun createUsesPermission(name: String): JSONObject {
    return JSONObject().put("node_type", "element").put("name", "uses-permission").put(
        "attributes", JSONArray().put(
            JSONObject().put("name", "name").put("id", 16842755)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", name)
        )
    )
}

fun createProperty(key: String, value: String): JSONObject {
    return JSONObject().put("node_type", "element").put("name", "property").put(
        "attributes", JSONArray().put(
            JSONObject().put("name", "name").put("id", 16842755)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", key)
        ).put(
            JSONObject().put("name", "value").put("id", 16842788)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", value)
        )
    )
}

private const val PATCH_NAME = "patch_vr_metadata"

// Elements without children have no "nodes" array yet, so create one instead of silently skipping them.
private fun JSONArray?.putXrProperties(boundary: String): JSONArray {
    return (this ?: JSONArray()).put(
        createProperty(
            "android.window.PROPERTY_XR_ACTIVITY_START_MODE",
            "XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED"
        )
    ).put(
        createProperty(
            "android.window.PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED",
            "XR_BOUNDARY_TYPE_$boundary"
        )
    )
}

/**
 * Arguments (all optional):
 * - `boundary=LARGE|NO_RECOMMENDATION` (default LARGE)
 * - `start_mode_scope=activity|application|both` (default activity) – where the Android XR properties go
 * - `openxr_loader=library|native|both` (default both) – how libopenxr.google.so is declared;
 *   Android XR documents `uses-native-library`, upstream overport used `uses-library`
 */
val PATCH_VR_METADATA = Patch(PATCH_NAME) { arguments ->
    val options = arguments.keyValueArguments(PATCH_NAME, setOf("boundary", "start_mode_scope", "openxr_loader"))
    val boundary = options.choice(PATCH_NAME, "boundary", "LARGE", setOf("LARGE", "NO_RECOMMENDATION"))
    val scope = options.choice(PATCH_NAME, "start_mode_scope", "activity", setOf("activity", "application", "both"))
    val loader = options.choice(PATCH_NAME, "openxr_loader", "both", setOf("library", "native", "both"))

    selectManifestJson {
        takeNodesEach({ named("manifest") }) {
            takeNodes {
                this?.put(createUsesFeature("android.software.xr.api.openxr", true))
                    ?.put(createUsesFeature("android.software.xr.api.spatial", true))
                    ?.put(createUsesFeature("android.hardware.xr.input.controller", false))
                    ?.put(createUsesFeature("android.hardware.xr.input.hand_tracking", false))
                    ?.put(createUsesFeature("android.hardware.xr.input.eye_tracking", false))
                    ?.put(createUsesPermission("org.khronos.openxr.permission.OPENXR"))
                    ?.put(createUsesPermission("org.khronos.openxr.permission.OPENXR_SYSTEM"))
                    ?.put(createUsesPermission("com.huawei.android.permission.VR"))
                    ?.put(createUsesPermission("android.permission.EYE_TRACKING_COARSE"))
                    ?.put(createUsesPermission("android.permission.EYE_TRACKING_FINE"))
                    ?.put(createUsesPermission("android.permission.FACE_TRACKING"))
                    ?.put(createUsesPermission("android.permission.HAND_TRACKING"))
                    ?.put(createUsesPermission("android.permission.SCENE_UNDERSTANDING_COARSE"))
                    ?.put(createUsesPermission("android.permission.SCENE_UNDERSTANDING_FINE"))
            }
            takeNodesEach({ named("application") }) {
                if (scope != "application") {
                    takeNodesEach({ named("activity") }) {
                        takeNodes { putXrProperties(boundary) }
                    }
                }
                takeNodesEach({ named("meta-data") }) {
                    if (nameAttribute() == "com.oculus.supportedDevices") null else this
                }
                takeNodes {
                    val nodes = this ?: JSONArray()
                    if (scope != "activity") {
                        nodes.putXrProperties(boundary)
                    }
                    if (loader != "native") {
                        nodes.put(createUsesLibrary("libopenxr.google.so", false))
                    }
                    if (loader != "library") {
                        nodes.put(createUsesNativeLibrary("libopenxr.google.so", false))
                    }
                    nodes.put(createMetadata("pvr.app.type", "vr"))
                        ?.put(createMetadata("com.yvr.intent.category.VR", "vr_only"))
                        ?.put(createMetadata("com.oculus.supportedDevices", "all"))
                        ?.put(createMetadata("com.huawei.android.vr.application.mode", "vr_only"))
                        ?.put(createMetadata("com.huawei.android.vr.application.type", "game"))
                        ?.put(createMetadata("com.huawei.vr.application.freeDegree", "3dof|6dof"))
                        ?.put(createMetadataFloat("android.max_aspect", 2.1f))
                        ?.put(createMetadata("minWaveSDKVersion", "1"))
                        ?.put(createMetadata("com.htc.vr.content.NumController", "1,2"))
                        ?.put(createMetadata("com.htc.vr.content.NumDoFController", "3,6DoF"))
                        ?.put(createMetadata("com.htc.vr.content.NumDoFHmd", "3,6DoF"))
                }
            }
        }
    }
}
