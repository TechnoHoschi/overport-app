package moe.crx.overport.patches

import moe.crx.overport.config.addProducts
import moe.crx.overport.patching.InvalidPatchArgumentException
import moe.crx.overport.patching.Patch

/**
 * Marks in-app products (SKUs) as owned in overport's platform emulation, e.g. a premium unlock
 * you have bought on your Quest. Example: `patch_platform_products=premium_unlock,another_sku`.
 */
val PATCH_PLATFORM_PRODUCTS = Patch("patch_platform_products", false, true) { arguments ->
    val products = arguments.map { it.trim() }.filter { it.isNotEmpty() }

    if (products.isEmpty()) {
        throw InvalidPatchArgumentException("patch_platform_products needs at least one SKU (patch_platform_products=<sku>,<sku>).")
    }

    selectConfig {
        addProducts(products)
    }
}
