package org.mundocreativo.barronegro.data

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Escribe el CSV en la caché de la app y abre la hoja de compartir/guardar del sistema.
 *
 * La carpeta `cache/reportes` queda expuesta por el `FileProvider` declarado en el
 * manifiesto (`${applicationId}.archivos`), así que no hace falta ningún permiso de
 * almacenamiento: el usuario decide si lo guarda en Archivos, Drive o lo envía.
 */
actual fun guardarCsv(nombreArchivo: String, contenido: String): Boolean {
    val contexto = ContextoApp.contexto ?: return false
    return runCatching {
        val carpeta = File(contexto.cacheDir, "reportes").apply { mkdirs() }
        val archivo = File(carpeta, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
        val compartir = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nombreArchivo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val selector = Intent.createChooser(compartir, "Guardar o compartir el reporte")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(selector)
        true
    }.getOrDefault(false)
}
