package org.mundocreativo.barronegro.data

import org.mundocreativo.barronegro.Formato
import org.mundocreativo.barronegro.MesCalendario

/**
 * CSV del reporte mensual de reservas.
 *
 * Se usa `;` como separador porque es el que espera Excel en español (con `,` abre
 * todo en una sola columna). El archivo lleva BOM UTF-8 para que Excel conserve las
 * tildes, saltos CRLF y comillas dobles según RFC 4180.
 *
 * Columnas: nombre, correo, teléfono, fecha, hora de inicio, hora de fin, duración en
 * minutos, estado, origen y comentario. El correo de una reserva con cuenta lo aporta
 * `auth.users`; el de una anónima, `correo_contacto` (lo resuelve Postgres).
 */
object ReporteCsv {

    /** Separador de campos. Cambiarlo a ',' produce un CSV estándar. */
    const val SEPARADOR: Char = ';'

    private const val SALTO = "\r\n"
    private const val BOM = "\uFEFF"

    private val CABECERA = listOf(
        "Nombre", "Correo", "Teléfono", "Fecha", "Hora inicio", "Hora fin",
        "Duración (min)", "Estado", "Origen", "Comentario",
    )

    /** `reservas-2026-11.csv` */
    fun nombreArchivo(mes: MesCalendario): String =
        "reservas-${mes.anio}-${mes.mes.toString().padStart(2, '0')}.csv"

    /** Contenido completo del CSV, ordenado por fecha y hora de inicio. */
    fun generar(filas: List<FilaReporte>): String = buildString {
        append(BOM)
        append(CABECERA.joinToString(SEPARADOR.toString()) { escapar(it) })
        append(SALTO)
        filas.sortedWith(compareBy({ it.fecha }, { it.horaInicio })).forEach { fila ->
            val valores = listOf(
                fila.nombre,
                fila.correo.orEmpty(),
                fila.telefono.orEmpty(),
                fila.fecha,
                Formato.hora(fila.horaInicio),
                Formato.hora(fila.horaFin),
                fila.duracionMin.toString(),
                Formato.etiquetaEstado(fila.estado),
                if (fila.esAnonima) "Anónima" else "Cuenta",
                fila.comentario.orEmpty(),
            )
            append(valores.joinToString(SEPARADOR.toString()) { escapar(it) })
            append(SALTO)
        }
    }

    private fun escapar(valor: String): String =
        if (valor.any { it == SEPARADOR || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + valor.replace("\"", "\"\"") + "\""
        } else {
            valor
        }
}
