package org.mundocreativo.barronegro

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * `true` solo en Android. La contraseña para el inicio automático nunca se escribe en el navegador:
 * en web `localStorage` es accesible desde cualquier script del sitio.
 */
expect val esAndroid: Boolean