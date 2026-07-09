package com.browicy.model

import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Neuer Tab",
    val url: String = "about:blank",
)
