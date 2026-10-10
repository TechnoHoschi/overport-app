package moe.crx.overport.patching

data class Patch(
    val name: String,
    val isRecommended: Boolean = true,
    /** Patch is useless without arguments; GUIs without argument input should hide it. */
    val requiresArguments: Boolean = false,
    val executor: PatchExecutor.(List<String>) -> Unit,
)
