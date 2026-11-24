package org.mundocreativo.barronegro.data

import com.russhwolf.settings.Settings
import org.mundocreativo.barronegro.esAndroid

/** Copia local mínima del perfil para mostrar el panel correcto antes de que responda la red. */
data class PerfilRecordado(val nombre: String, val rol: String)

/**
 * Preferencias locales de la app.
 *
 * En Android se guardan en `SharedPreferences` (archivo `org.mundocreativo.barronegro_preferences`)
 * y en web en `localStorage`. La sesión de Supabase ya se persiste por su cuenta; aquí se guarda
 * además el correo, una copia del perfil y la pestaña abierta, de modo que el administrador vuelva
 * directamente a su panel.
 *
 * La contraseña para el inicio automático se guarda **solo en Android** ([esAndroid]): en web
 * `localStorage` es legible desde cualquier script del sitio. Se borra al cerrar sesión y cuando el
 * inicio automático falla.
 */
object PreferenciasApp {

    private const val CLAVE_CORREO = "barronegro.correo"
    private const val CLAVE_CONTRASENA = "barronegro.contrasena"
    private const val CLAVE_NOMBRE = "barronegro.nombre"
    private const val CLAVE_ROL = "barronegro.rol"
    private const val CLAVE_PESTANA = "barronegro.pestana"
    private const val CLAVE_SESION_ANONIMA = "barronegro.sesion_anonima"

    private val ajustes: Settings by lazy { Settings() }

    // --- Correo -------------------------------------------------------------

    fun correoRecordado(): String? = leer { ajustes.getStringOrNull(CLAVE_CORREO) }

    // --- Credenciales (solo Android) ---------------------------------------

    fun contrasenaRecordada(): String? =
        if (esAndroid) leer { ajustes.getStringOrNull(CLAVE_CONTRASENA) } else null

    fun recordarCredenciales(correo: String, contrasena: String) {
        if (!esAndroid || correo.isBlank() || contrasena.isBlank()) return
        escribir {
            ajustes.putString(CLAVE_CORREO, correo)
            ajustes.putString(CLAVE_CONTRASENA, contrasena)
        }
    }

    fun olvidarContrasena() = escribir { ajustes.remove(CLAVE_CONTRASENA) }

    // --- Perfil -------------------------------------------------------------

    fun perfilRecordado(): PerfilRecordado? = leer {
        val nombre = ajustes.getStringOrNull(CLAVE_NOMBRE) ?: return@leer null
        PerfilRecordado(nombre = nombre, rol = ajustes.getStringOrNull(CLAVE_ROL) ?: "usuario")
    }

    fun recordarPerfil(nombre: String, rol: String) = escribir {
        ajustes.putString(CLAVE_NOMBRE, nombre)
        ajustes.putString(CLAVE_ROL, rol)
    }

    fun olvidarPerfil() = escribir {
        ajustes.remove(CLAVE_NOMBRE)
        ajustes.remove(CLAVE_ROL)
    }

    // --- Navegación ---------------------------------------------------------

    fun pestanaRecordada(): Int? = leer { ajustes.getIntOrNull(CLAVE_PESTANA) }

    fun recordarPestana(indice: Int) = escribir { ajustes.putInt(CLAVE_PESTANA, indice) }

    // --- Sesión anónima -----------------------------------------------------

    fun sesionAnonima(): String? = leer { ajustes.getStringOrNull(CLAVE_SESION_ANONIMA) }

    fun recordarSesionAnonima(valor: String) = escribir { ajustes.putString(CLAVE_SESION_ANONIMA, valor) }

    fun olvidarSesionAnonima() = escribir { ajustes.remove(CLAVE_SESION_ANONIMA) }

    // Un fallo de almacenamiento (preview, tests, permisos) nunca debe romper la app.
    private fun <T> leer(bloque: () -> T?): T? = runCatching(bloque).getOrNull()

    private fun escribir(bloque: () -> Unit) {
        // Las preferencias son una comodidad, no un requisito: se ignora cualquier fallo.
        runCatching { bloque() }
    }
}
