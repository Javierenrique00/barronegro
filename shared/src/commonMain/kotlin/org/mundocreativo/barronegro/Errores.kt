package org.mundocreativo.barronegro

/** Traduce los errores de Supabase a mensajes en español para la interfaz. */
fun mensajeDeError(t: Throwable): String {
    val bruto = (t.message ?: t.toString()).trim()

    // El orden importa: se devuelve la primera coincidencia, así que los códigos concretos
    // van antes que las frases genéricas.
    val conocido = listOf(
        "over_email_send_rate_limit" to "Se han enviado demasiados correos. Espera unos minutos.",
        "email_address_not_authorized" to "El servidor de correo del proyecto no puede enviar a esa dirección. Configura un SMTP propio.",
        "Email address not authorized" to "El servidor de correo del proyecto no puede enviar a esa dirección. Configura un SMTP propio.",
        "Email rate limit exceeded" to "Se han enviado demasiados correos. Espera unos minutos.",
        "Email link is invalid or has expired" to "El enlace del correo no es válido o ha caducado. Solicita uno nuevo.",
        "New password should be different from the old password" to "La nueva contraseña debe ser distinta de la anterior.",
        "Invalid login credentials" to "Correo o contraseña incorrectos.",
        "Email not confirmed" to "Debes confirmar tu correo antes de entrar.",
        "User already registered" to "Ese correo ya está registrado.",
        "Password should be at least" to "La contraseña es demasiado corta (mínimo 8 caracteres).",
        "Unable to validate email address" to "El correo no tiene un formato válido.",
        "Signups not allowed" to "El registro está desactivado en este proyecto.",
        "Rate limit" to "Demasiados intentos. Espera un momento.",
    )
    conocido.firstOrNull { bruto.contains(it.first, ignoreCase = true) }?.let { return it.second }

    val extraido = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(bruto)?.groupValues?.get(1)
    val limpio = (extraido ?: bruto.substringBefore('\n')).trim()
    return if (limpio.isBlank()) "Ha ocurrido un error inesperado." else limpio.take(220)
}
