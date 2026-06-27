package com.beezlist.tv.data

data class Channel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String = "",
    val tvgId: String? = null,
)
