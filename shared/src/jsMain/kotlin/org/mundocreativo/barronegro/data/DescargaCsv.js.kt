package org.mundocreativo.barronegro.data

import web.dom.document
import web.html.HtmlTagName

/**
 * Descarga el CSV con un enlace temporal y una URL `data:`.
 * Se ejecuta dentro del clic del botón, así el navegador no bloquea la descarga.
 */
actual fun guardarCsv(nombreArchivo: String, contenido: String): Boolean = runCatching {
    val enlace = document.createElement(HtmlTagName.a)
    enlace.href = "data:text/csv;charset=utf-8," + codificarDatos(contenido)
    enlace.download = nombreArchivo
    enlace.style.visibility = "hidden"
    document.body.appendChild(enlace)
    enlace.click()
    enlace.remove()
    true
}.getOrDefault(false)
