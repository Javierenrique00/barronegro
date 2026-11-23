package org.mundocreativo.barronegro

/** Traduce los errores de Supabase a mensajes en español para la interfaz. */
fun mensajeDeError(t: Throwable): String {
    val bruto = (t.message ?: t.toString()).trim()

    val conocido = listOf(
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
