package moe.crx.overport.patches

import com.reandroid.json.JSONObject
import moe.crx.overport.patching.Patch
import moe.crx.overport.patching.choice
import moe.crx.overport.patching.keyValueArguments
import moe.crx.overport.utils.*

private const val PATCH_NAME = "patch_remove_uses_library"

fun createRequired(required: Boolean): JSONObject {
    return JSONObject().put("name", "required").put("id", 16843406)
        .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android").put("value_type", "BOOLEAN")
        .put("data", required)
}

private fun JSONObject?.markOptional(): JSONObject? {
    return takeAttributes {
        takeEach<JSONObject>({ named("required") }) { null }
        this?.put(createRequired(false))
    }
}

/**
 * Quest apps may require shared libraries that only exist on Quest, which blocks installation elsewhere.
 *
 * Arguments (optional):
 * - `mode=optional|remove` (default optional)
 *   - `optional`: keep every declaration but mark it `required=false`. Libraries that exist on the target
 *     device (e.g. org.apache.http.legacy) stay available, missing ones no longer block installation.
 *   - `remove`: upstream behaviour, drop every declaration except libopenxr.google.so.
 */
val PATCH_REMOVE_USES_LIBRARY = Patch(PATCH_NAME) { arguments ->
    val options = arguments.keyValueArguments(PATCH_NAME, setOf("mode"))
    val mode = options.choice(PATCH_NAME, "mode", "optional", setOf("optional", "remove"))

    selectManifestJson {
        takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("application") }) {
                listOf("uses-library", "uses-native-library").forEach { tag ->
                    takeNodesEach({ named(tag) }) {
                        when {
                            nameAttribute() == "libopenxr.google.so" -> this
                            mode == "remove" -> null
                            else -> markOptional()
                        }
                    }
                }
                this
            }
        }
    }
}
