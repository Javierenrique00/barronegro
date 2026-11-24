package org.mundocreativo.barronegro

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Fecha local del dispositivo en AAAA-MM-DD.
 *
 * No se usa `toISOString()`: devuelve la fecha en UTC y en America/Bogotá (UTC−5)
 * eso adelanta el día a partir de las 19:00, con lo que "hoy" sería mañana.
 */
@OptIn(ExperimentalWasmJsInterop::class)
actual fun fechaHoyIso(): String = js(
    "(function(){var d=new Date();" +
        "return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');})()",
)
