package org.mundocreativo.barronegro

import org.mundocreativo.barronegro.data.FilaReporte
import org.mundocreativo.barronegro.data.ReporteCsv
import org.mundocreativo.barronegro.data.codificarDatos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReporteCsvTest {

    private fun fila(
        id: String = "reserva-1",
        fecha: String = "2026-11-25",
        inicio: String = "09:00:00",
        fin: String = "11:00:00",
        duracion: Int = 120,
        estado: String = "aprobada",
        origen: String = "cuenta",
        nombre: String = "Ana Pérez",
        correo: String? = "ana@example.com",
        telefono: String? = "3001234567",
        comentario: String? = null,
    ) = FilaReporte(
        reservaId = id,
        fecha = fecha,
        horaInicio = inicio,
        horaFin = fin,
        duracionMin = duracion,
        estado = estado,
        origen = origen,
        nombre = nombre,
        correo = correo,
        telefono = telefono,
        comentario = comentario,
    )

    /** Filas del CSV sin el BOM y sin el último salto. */
    private fun filas(csv: String): List<String> =
        csv.removePrefix("\uFEFF").trimEnd('\r', '\n').split("\r\n")

    @Test
    fun laCabeceraLlevaBomYSaltoCrLf() {
        val csv = ReporteCsv.generar(emptyList())
        assertTrue(
            csv.startsWith(
                "\uFEFFNombre;Correo;Teléfono;Fecha;Hora inicio;Hora fin;" +
                        "Duración (min);Estado;Origen;Comentario\r\n",
            ),
        )
        assertEquals(1, filas(csv).size)
    }

    @Test
    fun cadaCitaOcupaUnaFilaConTodosSusDatos() {
        val csv = ReporteCsv.generar(listOf(fila()))
        assertEquals(
            "Ana Pérez;ana@example.com;3001234567;2026-11-25;09:00;11:00;" +
                    "120;Confirmada;Cuenta;",
            filas(csv)[1],
        )
    }

    @Test
    fun usaElNombreDelArchivoConElMes() {
        assertEquals("reservas-2026-11.csv", ReporteCsv.nombreArchivo(MesCalendario(2026, 11)))
        assertEquals("reservas-2027-01.csv", ReporteCsv.nombreArchivo(MesCalendario(2027, 1)))
    }

    @Test
    fun ordenaPorFechaYHoraDeInicio() {
        val csv = ReporteCsv.generar(
            listOf(
                fila(id = "b", fecha = "2026-11-26", inicio = "08:00:00", nombre = "Bárbara"),
                fila(id = "a", fecha = "2026-11-25", inicio = "17:00:00", nombre = "Andrés"),
            ),
        )
        val datos = filas(csv).drop(1)
        assertEquals("Andrés", datos[0].substringBefore(';'))
        assertEquals("Bárbara", datos[1].substringBefore(';'))
    }

    @Test
    fun entrecomillaSeparadoresComillasYSaltosDeLinea() {
        val csv = ReporteCsv.generar(
            listOf(
                fila(
                    nombre = "Pérez; Ana \"la jefa\"",
                    comentario = "Primera línea\nSegunda línea",
                ),
            ),
        )
        assertTrue(csv.contains("\"Pérez; Ana \"\"la jefa\"\"\""))
        assertTrue(csv.contains("\"Primera línea\nSegunda línea\""))
    }

    @Test
    fun distingueReservasAnonimasYEstados() {
        val csv = ReporteCsv.generar(
            listOf(
                fila(estado = "cancelada", origen = "anonima", telefono = null, correo = "anon@example.com"),
            ),
        )
        val datos = filas(csv)[1]
        assertTrue(datos.contains(";Cancelada;Anónima;"))
        assertTrue(datos.contains(";;2026-11-25"))
    }

    @Test
    fun codificaElContenidoComoUrlDeDatos() {
        assertEquals("Hola%20mundo", codificarDatos("Hola mundo"))
        assertEquals("%C3%A1%3B%22", codificarDatos("á;\""))
        assertEquals("%EF%BB%BF", codificarDatos("\uFEFF"))
        assertEquals("reservas-2026-11.csv", codificarDatos("reservas-2026-11.csv"))
    }
}
