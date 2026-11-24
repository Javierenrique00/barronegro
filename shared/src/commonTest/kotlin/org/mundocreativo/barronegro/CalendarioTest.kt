package org.mundocreativo.barronegro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarioTest {

    @Test
    fun febreroRespetaLosAniosBisiestos() {
        assertEquals(29, MesCalendario(2028, 2).diasEnMes())
        assertEquals(28, MesCalendario(2026, 2).diasEnMes())
        assertEquals(29, MesCalendario(2000, 2).diasEnMes())
        assertEquals(28, MesCalendario(1900, 2).diasEnMes())
    }

    @Test
    fun elRangoDelMesCubreTodosLosDias() {
        val mes = MesCalendario(2026, 11)
        assertEquals("2026-11-01", mes.primerDiaIso())
        assertEquals("2026-11-30", mes.ultimoDiaIso())
        assertEquals(30, mes.diasEnMes())
    }

    @Test
    fun elPrimerDiaSemanaSigueAlCalendario() {
        // El 1 de noviembre de 2026 fue domingo y el 1 de diciembre, martes.
        assertEquals(7, MesCalendario(2026, 11).primerDiaSemana())
        assertEquals(2, MesCalendario(2026, 12).primerDiaSemana())
    }

    @Test
    fun sumarMesesCruzaElAnio() {
        assertEquals(MesCalendario(2027, 1), MesCalendario(2026, 12).sumar(1))
        assertEquals(MesCalendario(2025, 12), MesCalendario(2026, 1).sumar(-1))
        assertEquals(MesCalendario(2027, 2), MesCalendario(2026, 11).sumar(3))
        assertEquals(MesCalendario(2026, 6), MesCalendario(2026, 11).sumar(-5))
    }

    @Test
    fun sumarDiasCruzaMesYAnio() {
        assertEquals("2026-12-01", sumarDias("2026-11-30", 1))
        assertEquals("2027-01-01", sumarDias("2026-12-31", 1))
        assertEquals("2028-02-29", sumarDias("2028-02-28", 1))
        assertEquals("2026-11-22", sumarDias("2026-11-23", -1))
        assertEquals("2026-11-23", sumarDias("2026-11-23", 0))
    }

    @Test
    fun diasEntreCuentaLosDiasTranscurridos() {
        assertEquals(30, diasEntre("2026-11-01", "2026-12-01"))
        assertEquals(1, diasEntre("2026-11-23", "2026-11-24"))
        assertEquals(365, diasEntre("2026-01-01", "2027-01-01"))
    }

    @Test
    fun anteriorAComparaMeses() {
        assertTrue(MesCalendario(2026, 10).anteriorA(MesCalendario(2026, 11)))
        assertFalse(MesCalendario(2026, 11).anteriorA(MesCalendario(2026, 11)))
        assertFalse(MesCalendario(2027, 1).anteriorA(MesCalendario(2026, 12)))
    }
}
