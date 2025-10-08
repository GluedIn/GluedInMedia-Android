package com.gluedin.media.and.shopify

import androidx.annotation.Keep

@Keep
data class Product(
    val id: String,
    val title: String,
    val description: String,
    val price: String,
    val currency: String,
    val imageUrl: String,
    val variantsOptions: List<String>,
    val variantsId: List<String>,
    val variantName: String,
    val availableForSale: Boolean,
)
