package org.mundocreativo.barronegro.data

/**
 * Entrega un CSV al usuario.
 *
 * - **Web (JS/Wasm)**: descarga el archivo con su nombre.
 * - **Android**: lo escribe en la caché y abre la hoja de compartir/guardar del sistema.
 *
 * Devuelve `false` si la plataforma no pudo entregar el archivo (por ejemplo, si el
 * navegador bloqueó la descarga o la app no tiene contexto todavía).
 */
expect fun guardarCsv(nombreArchivo: String, contenido: String): Boolean

/**
 * Codificación porcentual para una URL `data:` (RFC 3986), en Kotlin puro.
 * Se usa en web para no depender de `Blob`/`JsAny` en Wasm: el texto se codifica
 * byte a byte en UTF-8 y solo quedan literales los caracteres no reservados.
 */
internal fun codificarDatos(contenido: String): String {
    val bytes = contenido.encodeToByteArray()
    val salida = StringBuilder(bytes.size * 3)
    for (byte in bytes) {
        val valor = byte.toInt() and 0xFF
        val caracter = valor.toChar()
        val noReservado = caracter in 'A'..'Z' || caracter in 'a'..'z' ||
            caracter in '0'..'9' || caracter == '-' || caracter == '_' ||
            caracter == '.' || caracter == '~'
        if (noReservado) {
            salida.append(caracter)
        } else {
            salida.append('%')
            salida.append(HEXADECIMAL[valor shr 4])
            salida.append(HEXADECIMAL[valor and 0x0F])
        }
    }
    return salida.toString()
}

private const val HEXADECIMAL = "0123456789ABCDEF"
