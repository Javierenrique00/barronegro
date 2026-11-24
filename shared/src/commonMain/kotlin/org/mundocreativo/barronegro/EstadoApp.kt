package org.mundocreativo.barronegro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.mundocreativo.barronegro.data.Bloqueo
import org.mundocreativo.barronegro.data.ConfirmacionReserva
import org.mundocreativo.barronegro.data.Contacto
import org.mundocreativo.barronegro.data.FilaAgenda
import org.mundocreativo.barronegro.data.FranjaDisponible
import org.mundocreativo.barronegro.data.Horario
import org.mundocreativo.barronegro.data.Perfil
import org.mundocreativo.barronegro.data.PreferenciasApp
import org.mundocreativo.barronegro.data.ReglaDisponibilidad
import org.mundocreativo.barronegro.data.Repositorio
import org.mundocreativo.barronegro.data.Reserva
import org.mundocreativo.barronegro.data.ReservaAnonima
import org.mundocreativo.barronegro.data.Sala
import org.mundocreativo.barronegro.data.borrarSesionAnonima
import org.mundocreativo.barronegro.data.guardarSesionAnonima
import org.mundocreativo.barronegro.data.leerSesionAnonima

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

    /** Sala con la política de reservas: duración, buffer, aprobación automática… */
    var sala by mutableStateOf<Sala?>(null)
        private set

    /** Franjas libres del mes visible, calculadas en Postgres. */
    var franjas by mutableStateOf<List<FranjaDisponible>>(emptyList())
        private set

    /** Plantilla semanal publicada (solo la usa el panel del administrador). */
    var reglas by mutableStateOf<List<ReglaDisponibilidad>>(emptyList())
        private set

    /** Reservas próximas con el nombre de cada persona (solo administradores). */
    var agenda by mutableStateOf<List<FilaAgenda>>(emptyList())
        private set

    /** Reservas hechas desde este navegador sin cuenta. */
    var reservasAnonimas by mutableStateOf<List<ReservaAnonima>>(emptyList())
        private set

    /** Sesión anónima actual (el valor de la cookie). */
    var sesionAnonima by mutableStateOf<String?>(null)
        private set

    /** Respuesta de la última reserva anónima para el diálogo de éxito. */
    var confirmacion by mutableStateOf<ConfirmacionReserva?>(null)
        private set

    /** Mes que muestra el calendario. */
    var mesCalendario by mutableStateOf(MesCalendario.actual())
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
                // La sesión de Supabase se restaura desde SharedPreferences. Si ya no existe (por
                // ejemplo tras un cierre de sesión en otro dispositivo) se intenta el inicio
                // automático con las credenciales guardadas.
                if (Repositorio.idUsuarioActual() == null) entrarConCredencialesGuardadas()
                conectado = Repositorio.idUsuarioActual() != null
                if (conectado) sembrarPerfilRecordado()

                // El calendario es público: se carga siempre, con o sin cuenta.
                val sesion = if (conectado) null else asegurarSesionAnonima()
                cargarPublico()
                if (conectado) {
                    cargarPrivado()
                } else if (sesion != null) {
                    cargarAnonimo(sesion)
                }
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
     * Inicio automático con las credenciales guardadas en las preferencias (solo Android). Si falla
     * —contraseña cambiada, sin red, cuenta bloqueada— se borra la contraseña para no reintentar en
     * cada arranque; el correo queda precargado en la pantalla de acceso.
     */
    private suspend fun entrarConCredencialesGuardadas() {
        val correo = PreferenciasApp.correoRecordado() ?: return
        val contrasena = PreferenciasApp.contrasenaRecordada() ?: return
        try {
            Repositorio.entrar(correo, contrasena)
        } catch (_: Throwable) {
            // Si la corrutina se canceló, la excepción debe seguir su curso.
            currentCoroutineContext().ensureActive()
            PreferenciasApp.olvidarContrasena()
        }
    }

    /**
     * Muestra de inmediato el nombre y el rol guardados para que la pestaña de administración
     * aparezca antes de que responda la red (y aunque la red falle).
     */
    private fun sembrarPerfilRecordado() {
        val id = Repositorio.idUsuarioActual() ?: return
        val guardado = PreferenciasApp.perfilRecordado() ?: return
        perfil = Perfil(id = id, nombreCompleto = guardado.nombre, rol = guardado.rol)
    }

    /**
     * Una sesión puede aparecer después de la carga inicial: al abrir el enlace del correo de
     * confirmación en Android, `handleDeeplinks` importa la sesión de forma asíncrona. Sin esto
     * la interfaz seguiría mostrando el formulario de acceso.
     */
    private suspend fun observarSesion() {
        Repositorio.estadoSesion().collect { autenticado ->
            if (!sesionInicializada) return@collect
            when {
                autenticado && !conectado -> {
                    conectado = true
                    aviso = "Sesión iniciada desde el enlace del correo."
                    try {
                        cargarPrivado()
                        cargarPublico()
                    } catch (t: Throwable) {
                        error = mensajeDeError(t)
                    }
                }

                !autenticado && conectado -> {
                    conectado = false
                    perfil = null
                    limpiarPrivado()
                    try {
                        val sesion = asegurarSesionAnonima()
                        cargarPublico()
                        cargarAnonimo(sesion)
                    } catch (t: Throwable) {
                        error = mensajeDeError(t)
                    }
                }
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
            limpiarPrivado()
            try {
                val sesion = asegurarSesionAnonima()
                cargarPublico()
                cargarAnonimo(sesion)
            } catch (t: Throwable) {
                error = mensajeDeError(t)
            }
            return
        }

        try {
            cargarPrivado()
            cargarPublico()
            conectado = true
        } catch (t: Throwable) {
            error = mensajeDeError(t)
            // La sesión guardada sigue siendo válida aunque falle la red: no devolver al formulario.
            conectado = Repositorio.idUsuarioActual() != null
        }
    }

    private suspend fun cargarPublico() {
        val salaRemota = Repositorio.salaPublica()
        sala = salaRemota
        calendarioAbierto = salaRemota?.calendarioAbierto ?: true
        franjas = Repositorio.franjasDisponibles(
            mesCalendario.primerDiaIso(),
            mesCalendario.ultimoDiaIso(),
        )
    }

    private suspend fun cargarPrivado() {
        val perfilRemoto = Repositorio.miPerfil()
        perfil = perfilRemoto
        if (perfilRemoto != null) {
            PreferenciasApp.recordarPerfil(perfilRemoto.nombreCompleto, perfilRemoto.rol)
        }
        val hoy = fechaHoyIso()
        horarios = Repositorio.horariosDesde(hoy)
        reservas = Repositorio.misReservas()
        bloqueos = Repositorio.bloqueos()
        reservasAnonimas = emptyList()
        if (perfil?.esAdmin == true) {
            pendientes = Repositorio.reservasPorResolver()
            reglas = Repositorio.reglasDisponibilidad()
            agenda = Repositorio.agendaAdmin()
        } else {
            pendientes = emptyList()
            reglas = emptyList()
            agenda = emptyList()
        }
    }

    private suspend fun cargarAnonimo(sesion: String) {
        reservasAnonimas = Repositorio.misReservasAnonimas(sesion)
    }

    private fun limpiarPrivado() {
        horarios = emptyList()
        reservas = emptyList()
        pendientes = emptyList()
        bloqueos = emptyList()
        reglas = emptyList()
        agenda = emptyList()
    }

    /**
     * Recupera la cookie local o pide a Postgres una sesión nueva. La sesión es solo un
     * identificador aleatorio; no crea ningún usuario ni guarda datos personales.
     */
    private suspend fun asegurarSesionAnonima(): String {
        val guardada = leerSesionAnonima()
        if (!guardada.isNullOrBlank() && guardada.matches(Regex("^[0-9a-f]{64}$"))) {
            sesionAnonima = guardada
            return guardada
        }
        val nueva = Repositorio.crearSesionAnonima()
        sesionAnonima = nueva.clave
        if (!guardarSesionAnonima(nueva.clave)) {
            aviso = "No pudimos guardar la cookie de este navegador. Podrás reservar, " +
                    "pero no ver tus reservas al volver."
        }
        return nueva.clave
    }

    fun cerrarConfirmacion() {
        confirmacion = null
    }

    /** Borra la cookie/sesión local. Las reservas siguen existiendo, pero este navegador las olvida. */
    fun olvidarNavegador() = accion("Este navegador ya no recordará tus reservas anónimas") {
        borrarSesionAnonima()
        sesionAnonima = null
        reservasAnonimas = emptyList()
    }

    fun entrar(correo: String, contrasena: String) = accion("Sesión iniciada") {
        Repositorio.entrar(correo.trim(), contrasena)
        conectado = Repositorio.idUsuarioActual() != null
        // Queda guardada para el próximo arranque: el administrador no vuelve a escribirla.
        if (conectado) PreferenciasApp.recordarCredenciales(correo.trim(), contrasena)
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
        // Se conserva el correo para precargarlo, pero nunca la contraseña ni el rol.
        PreferenciasApp.olvidarContrasena()
        PreferenciasApp.olvidarPerfil()
        conectado = false
        perfil = null
        horarios = emptyList()
        reservas = emptyList()
        pendientes = emptyList()
        bloqueos = emptyList()
        sala = null
        franjas = emptyList()
        reglas = emptyList()
        agenda = emptyList()
        mesCalendario = MesCalendario.actual()
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

    /**
     * Reserva directa de una franja del calendario. Postgres decide si queda confirmada
     * al instante (aprobación automática) o pendiente, y vuelve a validar todo.
     */
    fun reservar(
        franja: FranjaDisponible,
        duracionMin: Int,
        comentario: String?,
        tokenHumano: String,
        alTerminar: () -> Unit,
    ) = accion(
        if (sala?.aprobacionAutomatica != false) "Reserva confirmada"
        else "Solicitud enviada. El administrador la revisará.",
    ) {
        Repositorio.reservarFranja(
            fecha = franja.fecha,
            horaInicio = franja.horaInicio,
            duracionMin = duracionMin,
            comentario = comentario?.ifBlank { null },
            tokenHumano = tokenHumano,
        )
        alTerminar()
    }

    /**
     * Reserva anónima: el navegador se identifica con la sesión de la cookie. El correo y el
     * teléfono son obligatorios; Postgres vuelve a validarlos junto con la franja y el token.
     */
    fun reservarAnonimo(
        franja: FranjaDisponible,
        duracionMin: Int,
        nombre: String,
        correo: String,
        telefono: String,
        comentario: String?,
        tokenHumano: String,
        alTerminar: () -> Unit,
    ) {
        alcance.launch {
            trabajando = true
            try {
                val sesion = asegurarSesionAnonima()
                confirmacion = Repositorio.reservarFranjaAnonima(
                    fecha = franja.fecha,
                    horaInicio = franja.horaInicio,
                    duracionMin = duracionMin,
                    nombre = Contacto.nombre(nombre),
                    correo = correo,
                    telefono = telefono,
                    comentario = comentario?.ifBlank { null },
                    tokenHumano = tokenHumano,
                    sesion = sesion,
                )
                error = null
                alTerminar()
                try {
                    cargarPublico()
                    cargarAnonimo(sesion)
                } catch (t: Throwable) {
                    error = mensajeDeError(t)
                }
            } catch (t: Throwable) {
                error = mensajeDeError(t)
            } finally {
                trabajando = false
            }
        }
    }

    fun cancelarReservaAnonima(id: String, motivo: String? = null) =
        accion("Reserva cancelada") {
            val sesion = sesionAnonima
                ?: error("Este navegador no tiene una sesión anónima")
            Repositorio.cancelarReservaAnonima(id, sesion, motivo)
        }

    fun cancelarReservaAdmin(id: String, motivo: String? = null) =
        accion("Reserva cancelada y correo de cancelación solicitado") {
            Repositorio.cancelarReservaAdmin(id, motivo)
        }

    fun reenviarCorreoReserva(id: String, tipo: String? = null) =
        accion("Correo reenviado") {
            Repositorio.reenviarCorreoReserva(id, tipo)
        }

    /** Cambia el mes del calendario y recarga solo las franjas de ese mes. */
    fun cambiarMes(meses: Int) {
        val destino = mesCalendario.sumar(meses)
        if (destino.anteriorA(MesCalendario.actual())) return
        mesCalendario = destino
        alcance.launch {
            trabajando = true
            try {
                franjas = Repositorio.franjasDisponibles(destino.primerDiaIso(), destino.ultimoDiaIso())
                error = null
            } catch (t: Throwable) {
                error = mensajeDeError(t)
            } finally {
                trabajando = false
            }
        }
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

    /** Publica la plantilla semanal completa (reemplaza la anterior). */
    fun guardarDisponibilidad(nuevas: List<ReglaDisponibilidad>) =
        accion("Horario semanal guardado") {
            Repositorio.guardarDisponibilidad(nuevas)
        }

    fun guardarPolitica(
        duracionTurnoMin: Int,
        duracionMaximaMin: Int,
        bufferMin: Int,
        antelacionMinHoras: Int,
        diasMaxAntelacion: Int,
        capacidadPorTurno: Int,
        aprobacionAutomatica: Boolean,
        maxReservasPorDia: Int,
    ) = accion("Política de reservas actualizada") {
        Repositorio.actualizarPoliticaSala(
            duracionTurnoMin = duracionTurnoMin,
            duracionMaximaMin = duracionMaximaMin,
            bufferMin = bufferMin,
            antelacionMinHoras = antelacionMinHoras,
            diasMaxAntelacion = diasMaxAntelacion,
            capacidadPorTurno = capacidadPorTurno,
            aprobacionAutomatica = aprobacionAutomatica,
            maxReservasPorDia = maxReservasPorDia,
        )
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
