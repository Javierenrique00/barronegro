package org.mundocreativo.barronegro

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform