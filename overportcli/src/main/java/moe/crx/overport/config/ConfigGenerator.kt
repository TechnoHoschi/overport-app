package moe.crx.overport.config

import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private const val CURRENT_APPLICATION = "__current__"

private fun OverportConfig.updateCurrentApplication(block: OverportConfigApplication.() -> Unit) {
    val current = database.firstOrNull { it.appId == CURRENT_APPLICATION }
        ?: OverportConfigApplication(CURRENT_APPLICATION, listOf(), listOf())

    current.block()

    database = database.filter { it.appId != CURRENT_APPLICATION } + current
}

fun OverportConfig.updateDatabase(fridaString: String) {
    val config = runCatching {
        json.decodeFromString<FridaConfig>(fridaString)
    }.getOrNull() ?: return

    val products = config.interaction.parameters.skus
    val assets = config.interaction.parameters.assets.map { OverportConfigAsset(it.id, it.path, it.type) }

    // Merge instead of overwrite, so manually added products survive regardless of patch order.
    updateCurrentApplication {
        this.products = (this.products + products).distinct()
        this.assets = (this.assets + assets).distinctBy { it.assetId to it.filePath }
    }
}

fun OverportConfig.addProducts(products: List<String>) {
    updateCurrentApplication {
        this.products = (this.products + products).distinct()
    }
}
