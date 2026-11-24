package org.mundocreativo.barronegro

import org.mundocreativo.barronegro.data.Contacto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactoTest {

    @Test
    fun normalizaElCorreoAntesDeEnviarlo() {
        assertEquals("ana@example.com", Contacto.normalizarCorreo("  ANA@Example.COM "))
    }

    @Test
    fun aceptaCorreosHabituales() {
        assertTrue(Contacto.correoValido("ana@example.com"))
        assertTrue(Contacto.correoValido("ana.perez+taller@sub.example.co"))
        assertTrue(Contacto.correoValido("  ana@example.com  "))
    }

    @Test
    fun rechazaCorreosInvalidos() {
        assertFalse(Contacto.correoValido(""))
        assertFalse(Contacto.correoValido("ana"))
        assertFalse(Contacto.correoValido("ana@"))
        assertFalse(Contacto.correoValido("ana@example"))
        assertFalse(Contacto.correoValido("@example.com"))
        assertFalse(Contacto.correoValido("ana example@example.com"))
    }

    @Test
    fun elTelefonoSeGuardaSoloConDigitos() {
        assertEquals("573001234567", Contacto.normalizarTelefono("+57 300 123-4567"))
        assertEquals("", Contacto.normalizarTelefono("sin números"))
        assertTrue(Contacto.telefonoValido("+57 (300) 123-4567"))
        assertFalse(Contacto.telefonoValido("12345"))
        assertFalse(Contacto.telefonoValido("123456789012345678901"))
    }

    @Test
    fun elNombreVacioQuedaNulo() {
        assertEquals(null, Contacto.nombre("   "))
        assertEquals("Ana", Contacto.nombre("  Ana  "))
    }
}
