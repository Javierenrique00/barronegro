package org.mundocreativo.barronegro.data

/**
 * Validación y normalización de los datos de contacto del flujo anónimo.
 *
 * El servidor vuelve a validar todo; esto solo evita enviar formularios que ya se sabe
 * que Postgres rechazará. El correo se guarda en minúsculas y el teléfono sin separadores.
 */
object Contacto {

    private val correo = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val letrasTelefono = Regex("[^0-9]")

    fun normalizarCorreo(valor: String): String = valor.trim().lowercase()

    fun correoValido(valor: String): Boolean {
        val normalizado = normalizarCorreo(valor)
        return normalizado.length in 5..254 && correo.matches(normalizado)
    }

    fun normalizarTelefono(valor: String): String = letrasTelefono.replace(valor, "")

    fun telefonoValido(valor: String): Boolean =
        normalizarTelefono(valor).length in 7..20

    fun nombre(valor: String): String? =
        valor.trim().take(120).ifBlank { null }
}
