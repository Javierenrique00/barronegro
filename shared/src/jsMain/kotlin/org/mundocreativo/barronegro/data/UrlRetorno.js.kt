package org.mundocreativo.barronegro.data

/**
 * En web se usa la URL completa (origen + ruta) sin query ni fragmento, para que funcione tanto en
 * `http://localhost:8080/` como en `https://javierenrique00.github.io/barronegro/`.
 */
actual fun urlRetornoAuth(): String? =
    js("window.location.href.split('#')[0].split('?')[0]")
