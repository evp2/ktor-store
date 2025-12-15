package com.github.evp2.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(val name: String, val count: Int)