package com.gluedin.media.and.shopify

import androidx.annotation.Keep

@Keep
data class CartItem(
    val lineId: String,
    val title: String,
    val price: Double,
    val imageUrl: String,
    var quantity: Int,
    val variantsId: String,
    val variantTitle: String,
    val currencyCode: String,
    val variantName: String
)
