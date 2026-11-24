package org.mundocreativo.barronegro.data

import web.dom.document

private const val NOMBRE = NOMBRE_COOKIE_SESION

actual fun leerSesionAnonima(): String? =
    document.cookie
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("$NOMBRE=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

actual fun guardarSesionAnonima(valor: String): Boolean {
    if (valor.isBlank()) return false
    val seguro = if (document.location.protocol == "https:") "; Secure" else ""
    document.cookie = "$NOMBRE=$valor; Max-Age=31536000; Path=/; SameSite=Lax$seguro"
    return leerSesionAnonima() == valor
}

actual fun borrarSesionAnonima() {
    document.cookie = "$NOMBRE=; Max-Age=0; Path=/; SameSite=Lax"
}
