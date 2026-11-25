package org.mundocreativo.barronegro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mundocreativo.barronegro.Formato

@Serializable
data class Sala(
    val id: String,
    val nombre: String,
    val descripcion: String? = null,
    @SerialName("zona_horaria") val zonaHoraria: String = "America/Bogota",
    @SerialName("capacidad_por_turno") val capacidadPorTurno: Int = 1,
    @SerialName("calendario_abierto") val calendarioAbierto: Boolean = true,
    @SerialName("duracion_turno_min") val duracionTurnoMin: Int = 60,
    @SerialName("duracion_maxima_min") val duracionMaximaMin: Int = 120,
    @SerialName("buffer_min") val bufferMin: Int = 0,
    @SerialName("antelacion_min_horas") val antelacionMinHoras: Int = 0,
    @SerialName("dias_max_antelacion") val diasMaxAntelacion: Int = 60,
    @SerialName("aprobacion_automatica") val aprobacionAutomatica: Boolean = true,
    @SerialName("max_reservas_por_dia") val maxReservasPorDia: Int = 1,
    val activa: Boolean = true,
)

@Serializable
data class Perfil(
    val id: String,
    @SerialName("nombre_completo") val nombreCompleto: String = "",
    val telefono: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val rol: String = "usuario",
    val estado: String = "activo",
) {
    val esAdmin: Boolean get() = rol == "admin"
    val estaActivo: Boolean get() = estado == "activo"
    val nombreVisible: String get() = nombreCompleto.ifBlank { "Sin nombre" }
}

@Serializable
data class Horario(
    val id: String,
    @SerialName("sala_id") val salaId: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val capacidad: Int = 1,
    val estado: String = "disponible",
    val notas: String? = null,
) {
    val disponible: Boolean get() = estado == "disponible"
}

@Serializable
data class Reserva(
    val id: String,
    @SerialName("horario_id") val horarioId: String? = null,
    @SerialName("sala_id") val salaId: String? = null,
    @SerialName("usuario_id") val usuarioId: String? = null,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val estado: String,
    @SerialName("comentario_usuario") val comentarioUsuario: String? = null,
    @SerialName("comentario_admin") val comentarioAdmin: String? = null,
    @SerialName("propuesta_fecha") val propuestaFecha: String? = null,
    @SerialName("propuesta_hora_inicio") val propuestaHoraInicio: String? = null,
    @SerialName("propuesta_hora_fin") val propuestaHoraFin: String? = null,
    @SerialName("nombre_contacto") val nombreContacto: String? = null,
    @SerialName("correo_contacto") val correoContacto: String? = null,
    @SerialName("telefono_contacto") val telefonoContacto: String? = null,
    val origen: String = "cuenta",
    @SerialName("creado_en") val creadoEn: String? = null,
) {
    val esAnonima: Boolean get() = origen == "anonima"
}

@Serializable
data class Bloqueo(
    val id: String,
    @SerialName("sala_id") val salaId: String,
    val desde: String,
    val hasta: String,
    val motivo: String? = null,
)

/** Franja libre calculada en Postgres por `franjas_disponibles(...)`. */
@Serializable
data class FranjaDisponible(
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    /** Última hora a la que puede terminar la reserva dentro de la ventana del día. */
    @SerialName("hora_fin_maxima") val horaFinMaxima: String,
    @SerialName("cupos_libres") val cuposLibres: Int = 1,
) {
    /**
     * Duraciones que puede elegir la persona: múltiplos del turno, sin pasar del
     * máximo de la sala ni del cierre de la ventana de ese día.
     */
    fun duraciones(paso: Int, maximo: Int): List<Int> {
        if (paso <= 0) return listOf(maximo)
        val disponible = Formato.minutos(horaFinMaxima) - Formato.minutos(horaInicio)
        if (disponible <= 0) return listOf(paso)
        val tope = minOf(maximo, disponible)
        val opciones = mutableListOf<Int>()
        var actual = paso
        while (actual <= tope) {
            opciones += actual
            actual += paso
        }
        return opciones.ifEmpty { listOf(minOf(paso, disponible)) }
    }
}

/** Ventana de apertura de un día de la semana (1 = lunes … 7 = domingo). */
@Serializable
data class ReglaDisponibilidad(
    @SerialName("dia_semana") val diaSemana: Int,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
)

/**
 * Fila de `reporte_reservas(...)`: una reserva del mes con los datos de contacto
 * que necesita el reporte del administrador (nombre, correo, teléfono, duración).
 */
@Serializable
data class FilaReporte(
    @SerialName("reserva_id") val reservaId: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    @SerialName("duracion_min") val duracionMin: Int = 0,
    val estado: String,
    val origen: String = "cuenta",
    val nombre: String = "",
    val correo: String? = null,
    val telefono: String? = null,
    val comentario: String? = null,
) {
    val esAnonima: Boolean get() = origen == "anonima"

    /** Cita que ocupa cupo: pendiente, confirmada o con una nueva fecha propuesta. */
    val activa: Boolean get() = estado == "pendiente" || estado == "aprobada" || estado == "reprogramada"
}

/** Respuesta de `reservar_franja_anonima(...)`. */
@Serializable
data class ConfirmacionReserva(
    @SerialName("reserva_id") val reservaId: String,
    val codigo: String = "",
    val estado: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val correo: String = "",
    val nombre: String = "",
) {
    val confirmada: Boolean get() = estado == "aprobada"
}

/** Fila de `mis_reservas_anonimas(...)`, identificada por la cookie del navegador. */
@Serializable
data class ReservaAnonima(
    @SerialName("reserva_id") val reservaId: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val estado: String,
    @SerialName("nombre_contacto") val nombreContacto: String? = null,
    @SerialName("correo_contacto") val correoContacto: String? = null,
    @SerialName("telefono_contacto") val telefonoContacto: String? = null,
    @SerialName("comentario_usuario") val comentarioUsuario: String? = null,
    @SerialName("comentario_admin") val comentarioAdmin: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null,
) {
    val activa: Boolean get() = estado == "pendiente" || estado == "aprobada" || estado == "reprogramada"
}

/** Sesión aleatoria emitida por Postgres y guardada en la cookie del navegador. */
@Serializable
data class SesionAnonima(
    val clave: String,
    @SerialName("expira_en") val expiraEn: String? = null,
)

@Serializable
data class PiezaReto(
    val id: Int,
    val forma: String,
    val franjas: Int,
    val color: String,
    val rotacion: Int = 0,
)

@Serializable
data class MoldeReto(
    val forma: String,
    val franjas: Int,
)

@Serializable
data class RetoHumano(
    val id: String,
    val molde: MoldeReto,
    val piezas: List<PiezaReto>,
    val instruccion: String = "",
    @SerialName("expira_en") val expiraEn: String = "",
    @SerialName("min_ms") val minMs: Int = 1200,
)

@Serializable
data class TokenHumano(
    val token: String,
    @SerialName("expira_en") val expiraEn: String = "",
)
