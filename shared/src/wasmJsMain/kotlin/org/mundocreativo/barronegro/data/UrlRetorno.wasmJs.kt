package org.mundocreativo.barronegro.data

import kotlin.js.ExperimentalWasmJsInterop

/**
 * En web se usa la URL completa (origen + ruta) sin query ni fragmento, para que funcione tanto en
 * `http://localhost:8080/` como en `https://javierenrique00.github.io/barronegro/`.
 */
@OptIn(ExperimentalWasmJsInterop::class)
actual fun urlRetornoAuth(): String? =
    js("window.location.href.split('#')[0].split('?')[0]")
