package org.mundocreativo.barronegro

import kotlin.test.Test
import kotlin.test.assertEquals
import org.mundocreativo.barronegro.data.FranjaDisponible

class DisponibilidadTest {

    @Test
    fun minutosDeUnaHora() {
        assertEquals(540, Formato.minutos("09:00"))
        assertEquals(570, Formato.minutos("09:30:00"))
        assertEquals(0, Formato.minutos(""))
    }

    @Test
    fun sumarMinutosNoSeSaleDelDia() {
        assertEquals("10:30", Formato.sumarMinutos("09:00", 90))
        assertEquals("18:00", Formato.sumarMinutos("16:30", 90))
        assertEquals("00:30", Formato.sumarMinutos("23:30", 60))
    }

    @Test
    fun laDuracionSeLeeEnHorasYMinutos() {
        assertEquals("30 min", Formato.duracion(30))
        assertEquals("1 h", Formato.duracion(60))
        assertEquals("1 h 30 min", Formato.duracion(90))
        assertEquals("2 h", Formato.duracion(120))
    }

    @Test
    fun lasDuracionesRespetanLaVentanaYElMaximo() {
        val franja = FranjaDisponible(
            fecha = "2026-11-30",
            horaInicio = "09:00",
            horaFin = "10:00",
            horaFinMaxima = "12:00",
            cuposLibres = 1,
        )
        assertEquals(listOf(60, 120), franja.duraciones(paso = 60, maximo = 120))

        val ultimaHora = franja.copy(horaInicio = "11:00", horaFin = "12:00")
        assertEquals(listOf(60), ultimaHora.duraciones(paso = 60, maximo = 120))
    }

    @Test
    fun mediaHoraComoPasoGeneraCuatroOpciones() {
        val franja = FranjaDisponible("2026-11-30", "09:00", "09:30", "11:00", 1)
        assertEquals(listOf(30, 60, 90, 120), franja.duraciones(paso = 30, maximo = 120))
    }
}
