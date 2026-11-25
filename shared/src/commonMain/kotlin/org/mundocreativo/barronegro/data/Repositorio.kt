package org.mundocreativo.barronegro.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Todo el acceso a datos pasa por aquí. Las reglas de negocio viven en Postgres
 * (RPC + RLS), de modo que la web y la app no pueden comportarse distinto.
 */
object Repositorio {

    private const val SALA = SupabaseConfig.ID_SALA

    // --- Sesión -------------------------------------------------------------

    suspend fun inicializar() {
        supabase.auth.awaitInitialization()
    }

    fun idUsuarioActual(): String? = supabase.auth.currentUserOrNull()?.id

    fun correoActual(): String? = supabase.auth.currentUserOrNull()?.email

    suspend fun entrar(correo: String, contrasena: String) {
        supabase.auth.signInWith(Email) {
            email = correo
            password = contrasena
        }
    }

    suspend fun registrar(correo: String, contrasena: String, nombre: String) {
        supabase.auth.signUpWith(Email, urlRetornoAuth()) {
            email = correo
            password = contrasena
            data = buildJsonObject { put("nombre_completo", nombre) }
        }
    }

    suspend fun salir() {
        supabase.auth.signOut()
    }

    /** Envía el correo de recuperación. El enlace vuelve a la web o al enlace profundo de Android. */
    suspend fun recuperarContrasena(correo: String) {
        supabase.auth.resetPasswordForEmail(correo, urlRetornoAuth())
    }

    /** Cambia la contraseña del usuario con sesión activa (también tras abrir el enlace de recuperación). */
    suspend fun actualizarContrasena(nueva: String) {
        supabase.auth.updateUser { password = nueva }
    }

    /**
     * Emite `true` cuando hay sesión y `false` cuando no la hay. Sirve para reflejar en la
     * interfaz una sesión importada desde un enlace de correo (confirmación o recuperación).
     */
    fun estadoSesion(): Flow<Boolean> = supabase.auth.sessionStatus.mapNotNull { estado ->
        when (estado) {
            is SessionStatus.Authenticated -> true
            is SessionStatus.NotAuthenticated -> false
            else -> null
        }
    }

    // --- Lectura ------------------------------------------------------------

    suspend fun miPerfil(): Perfil? {
        val id = idUsuarioActual() ?: return null
        return supabase.postgrest.from("perfiles")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()
    }

    /** Datos públicos de la sala; también funciona para visitantes anónimos. */
    suspend fun salaPublica(): Sala? = supabase.postgrest.rpc(
        "sala_publica",
        buildJsonObject { put("p_sala_id", SALA) },
    ).decodeList<Sala>().firstOrNull()

    suspend fun horariosDesde(desde: String): List<Horario> = supabase.postgrest.from("horarios")
        .select {
            filter {
                eq("sala_id", SALA)
                gte("fecha", desde)
            }
            order("fecha", Order.ASCENDING)
            order("hora_inicio", Order.ASCENDING)
        }
        .decodeList()

    suspend fun misReservas(): List<Reserva> {
        val id = idUsuarioActual() ?: return emptyList()
        return supabase.postgrest.from("reservas")
            .select {
                filter { eq("usuario_id", id) }
                order("fecha", Order.DESCENDING)
            }
            .decodeList()
    }

    suspend fun reservasPorResolver(): List<Reserva> = supabase.postgrest.from("reservas")
        .select {
            filter { isIn("estado", listOf("pendiente", "reprogramada")) }
            order("fecha", Order.ASCENDING)
        }
        .decodeList()

    suspend fun bloqueos(): List<Bloqueo> = supabase.postgrest.from("bloqueos_sala")
        .select {
            filter { eq("sala_id", SALA) }
            order("desde", Order.ASCENDING)
        }
        .decodeList()

    /** Franjas libres del rango. La disponibilidad se calcula en Postgres. */
    suspend fun franjasDisponibles(desde: String, hasta: String): List<FranjaDisponible> =
        supabase.postgrest.rpc(
            "franjas_disponibles",
            buildJsonObject {
                put("p_sala_id", SALA)
                put("p_desde", desde)
                put("p_hasta", hasta)
            },
        ).decodeList()

    /** Plantilla semanal publicada por el administrador. */
    suspend fun reglasDisponibilidad(): List<ReglaDisponibilidad> =
        supabase.postgrest.from("disponibilidad_reglas")
            .select {
                filter { eq("sala_id", SALA) }
                order("dia_semana", Order.ASCENDING)
                order("hora_inicio", Order.ASCENDING)
            }
            .decodeList()

    // --- Verificación humana ------------------------------------------------

    /** Reto para una cuenta autenticada o para el navegador anónimo (con su sesión). */
    suspend fun crearReto(correo: String? = null, sesion: String? = null): RetoHumano =
        if (sesion == null) {
            supabase.postgrest.rpc("crear_reto_humano").decodeAs()
        } else {
            supabase.postgrest.rpc(
                "crear_reto_humano_anonimo",
                buildJsonObject {
                    put("p_sesion", sesion)
                    put("p_correo", correo.orEmpty())
                },
            ).decodeAs()
        }

    suspend fun verificarReto(
        retoId: String,
        piezaId: Int,
        duracionMs: Int,
        sesion: String? = null,
    ): TokenHumano =
        if (sesion == null) {
            supabase.postgrest.rpc(
                "verificar_reto_humano",
                buildJsonObject {
                    put("p_reto_id", retoId)
                    put("p_pieza_id", piezaId)
                    put("p_duracion_ms", duracionMs)
                },
            ).decodeAs()
        } else {
            supabase.postgrest.rpc(
                "verificar_reto_humano_anonimo",
                buildJsonObject {
                    put("p_reto_id", retoId)
                    put("p_pieza_id", piezaId)
                    put("p_duracion_ms", duracionMs)
                    put("p_sesion", sesion)
                },
            ).decodeAs()
        }

    // --- Reservas -----------------------------------------------------------

    /**
     * Reserva directa de una franja libre. Postgres vuelve a validar reglas, cupos,
     * bloqueos y el token humano antes de insertar; si la sala tiene aprobación
     * automática la reserva queda confirmada al instante.
     */
    suspend fun reservarFranja(
        fecha: String,
        horaInicio: String,
        duracionMin: Int,
        comentario: String?,
        tokenHumano: String,
    ): String = supabase.postgrest.rpc(
        "reservar_franja",
        buildJsonObject {
            put("p_sala_id", SALA)
            put("p_fecha", fecha)
            put("p_hora_inicio", horaInicio)
            put("p_duracion_min", duracionMin)
            put("p_comentario", comentario)
            put("p_token_humano", tokenHumano)
        },
    ).decodeAs()

    suspend fun crearSesionAnonima(): SesionAnonima =
        supabase.postgrest.rpc("crear_sesion_anonima").decodeAs()

    /** Reserva anónima identificada por la sesión de la cookie. */
    suspend fun reservarFranjaAnonima(
        fecha: String,
        horaInicio: String,
        duracionMin: Int,
        nombre: String?,
        correo: String,
        telefono: String,
        comentario: String?,
        tokenHumano: String,
        sesion: String,
    ): ConfirmacionReserva = supabase.postgrest.rpc(
        "reservar_franja_anonima",
        buildJsonObject {
            put("p_sala_id", SALA)
            put("p_fecha", fecha)
            put("p_hora_inicio", horaInicio)
            put("p_duracion_min", duracionMin)
            put("p_nombre", nombre)
            put("p_correo", Contacto.normalizarCorreo(correo))
            put("p_telefono", Contacto.normalizarTelefono(telefono))
            put("p_comentario", comentario)
            put("p_token_humano", tokenHumano)
            put("p_sesion", sesion)
        },
    ).decodeAs()

    suspend fun misReservasAnonimas(sesion: String): List<ReservaAnonima> =
        supabase.postgrest.rpc(
            "mis_reservas_anonimas",
            buildJsonObject { put("p_sesion", sesion) },
        ).decodeList()

    suspend fun cancelarReservaAnonima(reservaId: String, sesion: String, motivo: String? = null) {
        supabase.postgrest.rpc(
            "cancelar_reserva_anonima",
            buildJsonObject {
                put("p_reserva_id", reservaId)
                put("p_sesion", sesion)
                put("p_motivo", motivo)
            },
        )
    }

    suspend fun cancelarReservaAdmin(reservaId: String, motivo: String? = null) {
        supabase.postgrest.rpc(
            "cancelar_reserva_admin",
            buildJsonObject {
                put("p_reserva_id", reservaId)
                put("p_motivo", motivo)
            },
        )
    }

    suspend fun reenviarCorreoReserva(reservaId: String, tipo: String? = null) {
        supabase.postgrest.rpc(
            "reenviar_correo_reserva",
            buildJsonObject {
                put("p_reserva_id", reservaId)
                put("p_tipo", tipo)
            },
        )
    }

    /** Flujo anterior sobre franjas creadas a mano. Se conserva por compatibilidad. */
    suspend fun solicitarReserva(horarioId: String, comentario: String?, tokenHumano: String): String =
        supabase.postgrest.rpc(
            "solicitar_reserva",
            buildJsonObject {
                put("p_horario_id", horarioId)
                put("p_comentario", comentario)
                put("p_token_humano", tokenHumano)
            },
        ).decodeAs()

    suspend fun cancelarReserva(reservaId: String) {
        supabase.postgrest.rpc(
            "cancelar_reserva",
            buildJsonObject { put("p_reserva_id", reservaId) },
        )
    }

    suspend fun responderPropuesta(reservaId: String, aceptar: Boolean) {
        supabase.postgrest.rpc(
            "responder_propuesta",
            buildJsonObject {
                put("p_reserva_id", reservaId)
                put("p_aceptar", aceptar)
            },
        )
    }

    // --- Administración -----------------------------------------------------

    suspend fun crearHorarios(
        fechas: List<String>,
        horaInicio: String,
        horaFin: String,
        capacidad: Int,
    ): List<Horario> = supabase.postgrest.rpc(
        "crear_horarios",
        buildJsonObject {
            put("p_sala_id", SALA)
            putJsonArray("p_fechas") { fechas.forEach { add(it) } }
            put("p_hora_inicio", horaInicio)
            put("p_hora_fin", horaFin)
            put("p_capacidad", capacidad)
        },
    ).decodeList()

    suspend fun aprobarReserva(reservaId: String) {
        supabase.postgrest.rpc(
            "aprobar_reserva",
            buildJsonObject { put("p_reserva_id", reservaId) },
        )
    }

    suspend fun rechazarReserva(reservaId: String, motivo: String) {
        supabase.postgrest.rpc(
            "rechazar_reserva",
            buildJsonObject {
                put("p_reserva_id", reservaId)
                put("p_motivo", motivo)
            },
        )
    }

    suspend fun proponerReprogramacion(
        reservaId: String,
        fecha: String,
        horaInicio: String,
        horaFin: String,
        motivo: String?,
    ) {
        supabase.postgrest.rpc(
            "proponer_reprogramacion",
            buildJsonObject {
                put("p_reserva_id", reservaId)
                put("p_fecha", fecha)
                put("p_hora_inicio", horaInicio)
                put("p_hora_fin", horaFin)
                put("p_motivo", motivo)
            },
        )
    }

    suspend fun bloquearCalendario(desde: String, hasta: String, motivo: String?) {
        supabase.postgrest.rpc(
            "bloquear_calendario",
            buildJsonObject {
                put("p_sala_id", SALA)
                put("p_desde", desde)
                put("p_hasta", hasta)
                put("p_motivo", motivo)
            },
        )
    }

    suspend fun quitarBloqueo(bloqueoId: String) {
        supabase.postgrest.rpc(
            "quitar_bloqueo",
            buildJsonObject { put("p_bloqueo_id", bloqueoId) },
        )
    }

    suspend fun cambiarCalendario(abierto: Boolean) {
        supabase.postgrest.rpc(
            "cambiar_calendario_abierto",
            buildJsonObject {
                put("p_sala_id", SALA)
                put("p_abierto", abierto)
            },
        )
    }

    /** Reemplaza la plantilla semanal completa. Solo un administrador puede hacerlo. */
    suspend fun guardarDisponibilidad(reglas: List<ReglaDisponibilidad>) {
        supabase.postgrest.rpc(
            "guardar_disponibilidad",
            buildJsonObject {
                put("p_sala_id", SALA)
                putJsonArray("p_reglas") {
                    reglas.forEach { regla ->
                        add(
                            buildJsonObject {
                                put("dia_semana", regla.diaSemana)
                                put("hora_inicio", regla.horaInicio)
                                put("hora_fin", regla.horaFin)
                            },
                        )
                    }
                }
            },
        )
    }

    suspend fun actualizarPoliticaSala(
        duracionTurnoMin: Int,
        duracionMaximaMin: Int,
        bufferMin: Int,
        antelacionMinHoras: Int,
        diasMaxAntelacion: Int,
        capacidadPorTurno: Int,
        aprobacionAutomatica: Boolean,
        maxReservasPorDia: Int,
    ) {
        supabase.postgrest.rpc(
            "actualizar_politica_sala",
            buildJsonObject {
                put("p_sala_id", SALA)
                put("p_duracion_turno_min", duracionTurnoMin)
                put("p_duracion_maxima_min", duracionMaximaMin)
                put("p_buffer_min", bufferMin)
                put("p_antelacion_min_horas", antelacionMinHoras)
                put("p_dias_max_antelacion", diasMaxAntelacion)
                put("p_capacidad_por_turno", capacidadPorTurno)
                put("p_aprobacion_automatica", aprobacionAutomatica)
                put("p_max_reservas_por_dia", maxReservasPorDia)
            },
        )
    }

    /**
     * Reservas de un rango de fechas con nombre, correo, teléfono y duración
     * (solo administradores). Alimenta el calendario y el CSV del panel.
     */
    suspend fun reporteReservas(desde: String, hasta: String): List<FilaReporte> =
        supabase.postgrest.rpc(
            "reporte_reservas",
            buildJsonObject {
                put("p_sala_id", SALA)
                put("p_desde", desde)
                put("p_hasta", hasta)
            },
        ).decodeList()
}
