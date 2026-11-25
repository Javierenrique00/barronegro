package org.mundocreativo.barronegro.data

import android.content.Context

/**
 * Contexto de la aplicación, publicado por `AplicacionBarronegro`.
 *
 * `:shared` no tiene actividad propia y el reporte debe poder escribirse y compartirse
 * aunque la pantalla se recomonga; por eso basta con el contexto de la aplicación.
 */
object ContextoApp {
    @Volatile
    var contexto: Context? = null
}
