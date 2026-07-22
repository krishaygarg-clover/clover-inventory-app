package com.example.helloworld.models

import kotlinx.serialization.Serializable

@Serializable
data class FlashItem(
    val id: String,
    val name: String,
    val price: Long // Price in cents
)

@Serializable
data class FlashDeal(
    val itemId: String, // Can be "combo_id" for bundles
    val itemName: String,
    val originalPrice: Long,
    val flashPrice: Long,
    val expiryTimestamp: Long,
    val description: String = "",
    val isCombo: Boolean = false
)

@Serializable
data class FlashCombo(
    val id: String,
    val name: String,
    val itemIds: List<String>,
    val bundlePrice: Long,
    val description: String
)

@Serializable
data class CloverItemResponse(
    val elements: List<CloverItem> = emptyList()
)

@Serializable
data class CloverItem(
    val id: String? = null,
    val name: String? = null,
    val price: Long? = null
)

@Serializable
data class CloverAddItemRequest(
    val name: String,
    val price: Long
)
