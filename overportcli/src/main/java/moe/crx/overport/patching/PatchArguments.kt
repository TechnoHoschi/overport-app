package moe.crx.overport.patching

class InvalidPatchArgumentException(message: String) : IllegalArgumentException(message)

/**
 * Parses patch arguments of form `key=value` into a map.
 * Example: `--patches="patch_vr_metadata=boundary=NO_RECOMMENDATION,openxr_loader=native"`.
 */
fun List<String>.keyValueArguments(patchName: String, allowedKeys: Set<String>): Map<String, String> {
    return filter { it.isNotBlank() }.associate { argument ->
        val key = argument.substringBefore('=').trim()
        val value = argument.substringAfter('=', "").trim()

        if (key !in allowedKeys) {
            throw InvalidPatchArgumentException(
                "Unknown argument \"$key\" for $patchName. Allowed: ${allowedKeys.sorted().joinToString()}"
            )
        }
        if (value.isEmpty()) {
            throw InvalidPatchArgumentException("Argument \"$key\" for $patchName needs a value ($key=<value>).")
        }

        key to value
    }
}

fun Map<String, String>.choice(patchName: String, key: String, default: String, options: Set<String>): String {
    val value = this[key] ?: return default
    if (value !in options) {
        throw InvalidPatchArgumentException(
            "Invalid value \"$value\" for $patchName $key. Allowed: ${options.sorted().joinToString()}"
        )
    }
    return value
}
