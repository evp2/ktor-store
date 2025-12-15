package com.github.evp2.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val upc: Int,
    val name: String,
    val description: String,
    val price: Double
)