package org.mundocreativo.barronegro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mundocreativo.barronegro.data.Bloqueo
import org.mundocreativo.barronegro.data.Horario
import org.mundocreativo.barronegro.data.Perfil
import org.mundocreativo.barronegro.data.Repositorio
import org.mundocreativo.barronegro.data.Reserva

/** Estado compartido de la aplicación (web y Android usan exactamente el mismo). */
class EstadoApp(private val alcance: CoroutineScope) {

    var cargando by mutableStateOf(true)
        private set
    var trabajando by mutableStateOf(false)
        private set
    var conectado by mutableStateOf(false)
        private set
    var perfil by mutableStateOf<Perfil?>(null)
        private set
    var horarios by mutableStateOf<List<Horario>>(emptyList())
        private set
    var reservas by mutableStateOf<List<Reserva>>(emptyList())
        private set
    var pendientes by mutableStateOf<List<Reserva>>(emptyList())
        private set
    var bloqueos by mutableStateOf<List<Bloqueo>>(emptyList())
        private set
    var calendarioAbierto by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
    var aviso by mutableStateOf<String?>(null)

    val esAdmin: Boolean get() = perfil?.esAdmin == true

    /** Evita que la observación de sesión reaccione antes de que termine la carga inicial. */
    private var sesionInicializada = false

    fun iniciar() {
        alcance.launch {
            try {
                Repositorio.inicializar()
                conectado = Repositorio.idUsuarioActual() != null
                if (conectado) recargarInterno()
            } catch (t: Throwable) {
                error = mensajeDeError(t)
            } finally {
                cargando = false
                sesionInicializada = true
            }
        }
        alcance.launch { observarSesion() }
    }

    /**
     * Una sesión puede aparecer después de la carga inicial: al abrir el enlace del correo de
     * confirmación en Android, `handleDeeplinks` importa la sesión de forma asíncrona. Sin esto
     * la interfaz seguiría mostrando el formulario de acceso.
     */
    private suspend fun observarSesion() {
        Repositorio.estadoSesion().collect { autenticado ->
            if (sesionInicializada && autenticado && !conectado) {
                conectado = true
                aviso = "Sesión iniciada desde el enlace del correo. Ya puedes reservar tu franja."
                recargarInterno()
            }
        }
    }

    fun recargar() {
        alcance.launch { recargarInterno() }
    }

    private suspend fun recargarInterno() {
        if (Repositorio.idUsuarioActual() == null) {
            conectado = false
            perfil = null
            return
        }
        try {
            perfil = Repositorio.miPerfil()
            val hoy = fechaHoyIso()
            horarios = Repositorio.horariosDesde(hoy)
            reservas = Repositorio.misReservas()
            bloqueos = Repositorio.bloqueos()
            calendarioAbierto = Repositorio.sala()?.calendarioAbierto ?: true
            pendientes = if (perfil?.esAdmin == true) Repositorio.reservasPorResolver() else emptyList()
            conectado = true
        } catch (t: Throwable) {
            error = mensajeDeError(t)
        }
    }

    fun entrar(correo: String, contrasena: String) = accion("Sesión iniciada") {
        Repositorio.entrar(correo.trim(), contrasena)
        conectado = Repositorio.idUsuarioActual() != null
    }

    fun registrar(nombre: String, correo: String, contrasena: String) = accion(null) {
        Repositorio.registrar(correo.trim(), contrasena, nombre.trim())
        conectado = Repositorio.idUsuarioActual() != null
        if (!conectado) {
            aviso = "Revisa el correo ${correo.trim()} para confirmar la cuenta y después inicia sesión."
        }
    }

    fun salir() = accion("Sesión cerrada") {
        Repositorio.salir()
        conectado = false
        perfil = null
        horarios = emptyList()
        reservas = emptyList()
        pendientes = emptyList()
        bloqueos = emptyList()
    }

    fun recuperarContrasena(correo: String) =
        accion("Te enviamos un correo para restablecer la contraseña.") {
            Repositorio.recuperarContrasena(correo.trim())
        }

    fun cambiarContrasena(nueva: String) = accion("Contraseña actualizada") {
        Repositorio.actualizarContrasena(nueva)
    }

    fun solicitar(horario: Horario, comentario: String?, tokenHumano: String, alTerminar: () -> Unit) =
        accion("Solicitud enviada. El administrador la revisará.") {
            Repositorio.solicitarReserva(horario.id, comentario?.ifBlank { null }, tokenHumano)
            alTerminar()
        }

    fun cancelarReserva(id: String) = accion("Reserva cancelada") {
        Repositorio.cancelarReserva(id)
    }

    fun responderPropuesta(id: String, aceptar: Boolean) =
        accion(if (aceptar) "Propuesta aceptada" else "Propuesta rechazada") {
            Repositorio.responderPropuesta(id, aceptar)
        }

    fun crearHorarios(fechas: List<String>, inicio: String, fin: String, capacidad: Int) =
        accion("Horarios creados") {
            Repositorio.crearHorarios(fechas, inicio, fin, capacidad)
        }

    fun aprobarReserva(id: String) = accion("Reserva aprobada") {
        Repositorio.aprobarReserva(id)
    }

    fun rechazarReserva(id: String, motivo: String) = accion("Reserva rechazada") {
        Repositorio.rechazarReserva(id, motivo)
    }

    fun proponerReprogramacion(id: String, fecha: String, inicio: String, fin: String, motivo: String?) =
        accion("Propuesta enviada al usuario") {
            Repositorio.proponerReprogramacion(id, fecha, inicio, fin, motivo)
        }

    fun bloquearCalendario(desde: String, hasta: String, motivo: String?) =
        accion("Calendario bloqueado en ese rango") {
            Repositorio.bloquearCalendario(desde, hasta, motivo)
        }

    fun quitarBloqueo(id: String) = accion("Bloqueo eliminado") {
        Repositorio.quitarBloqueo(id)
    }

    fun cambiarCalendario(abierto: Boolean) =
        accion(if (abierto) "Calendario abierto" else "Calendario cerrado") {
            Repositorio.cambiarCalendario(abierto)
        }

    private fun accion(exito: String?, bloque: suspend () -> Unit) {
        alcance.launch {
            trabajando = true
            try {
                bloque()
                error = null
                if (exito != null) aviso = exito
                recargarInterno()
            } catch (t: Throwable) {
                error = mensajeDeError(t)
            } finally {
                trabajando = false
            }
        }
    }
}
