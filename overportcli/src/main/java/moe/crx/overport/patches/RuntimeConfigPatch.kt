package moe.crx.overport.patches

import moe.crx.overport.config.OverportConfig
import moe.crx.overport.patching.InvalidPatchArgumentException
import moe.crx.overport.patching.Patch
import moe.crx.overport.patching.keyValueArguments

private val INT_SETTERS: Map<String, OverportConfig.(Int) -> Unit> = mapOf(
    "verbose_logging" to { verboseLogging = it },
    "disable_controller_offset" to { disableControllerOffset = it },
    "force_passthrough" to { forcePassthrough = it },
    "disable_space_warp" to { disableSpaceWarp = it },
    "suggested_texture_width" to { suggestedTextureWidth = it },
    "suggested_texture_height" to { suggestedTextureHeight = it },
    "suggested_cpu_level" to { suggestedCpuLevel = it },
    "suggested_gpu_level" to { suggestedGpuLevel = it },
)

private val DOUBLE_SETTERS: Map<String, OverportConfig.(Double) -> Unit> = mapOf(
    "vibration_multiplier" to { vibrationMultiplier = it },
    "vibration_frequency_multiplier" to { vibrationFrequencyMultiplier = it },
)

/**
 * Writes arbitrary runtime settings into liboverport.config.so.
 * Example: `patch_runtime_config=verbose_logging=1,suggested_texture_width=2048`.
 */
val PATCH_RUNTIME_CONFIG = Patch("patch_runtime_config", false, true) { arguments ->
    val values = arguments.keyValueArguments("patch_runtime_config", INT_SETTERS.keys + DOUBLE_SETTERS.keys)

    selectConfig {
        values.forEach { (key, value) ->
            INT_SETTERS[key]?.let { setter ->
                val number = value.toIntOrNull()
                    ?: throw InvalidPatchArgumentException("$key expects an integer, got \"$value\".")
                setter(number)
            }
            DOUBLE_SETTERS[key]?.let { setter ->
                val number = value.toDoubleOrNull()
                    ?: throw InvalidPatchArgumentException("$key expects a number, got \"$value\".")
                setter(number)
            }
        }
    }
}
