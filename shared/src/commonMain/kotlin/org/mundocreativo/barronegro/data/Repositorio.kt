package org.mundocreativo.barronegro.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
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
        supabase.auth.signUpWith(Email) {
            email = correo
            password = contrasena
            data = buildJsonObject { put("nombre_completo", nombre) }
        }
    }

    suspend fun salir() {
        supabase.auth.signOut()
    }

    // --- Lectura ------------------------------------------------------------

    suspend fun miPerfil(): Perfil? {
        val id = idUsuarioActual() ?: return null
        return supabase.postgrest.from("perfiles")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()
    }

    suspend fun sala(): Sala? = supabase.postgrest.from("salas")
        .select { filter { eq("id", SALA) } }
        .decodeSingleOrNull()

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

    // --- Verificación humana ------------------------------------------------

    suspend fun crearReto(): RetoHumano =
        supabase.postgrest.rpc("crear_reto_humano").decodeAs()

    suspend fun verificarReto(retoId: String, piezaId: Int, duracionMs: Int): TokenHumano =
        supabase.postgrest.rpc(
            "verificar_reto_humano",
            buildJsonObject {
                put("p_reto_id", retoId)
                put("p_pieza_id", piezaId)
                put("p_duracion_ms", duracionMs)
            },
        ).decodeAs()

    // --- Reservas -----------------------------------------------------------

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
}
