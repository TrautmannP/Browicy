package com.browicy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform