package org.mundocreativo.barronegro

import android.app.Application
import org.mundocreativo.barronegro.data.ContextoApp

/**
 * Publica el contexto de la aplicación para que `:shared` pueda escribir el CSV del
 * reporte y abrir la hoja de compartir (`FileProvider`).
 */
class AplicacionBarronegro : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextoApp.contexto = applicationContext
    }
}
