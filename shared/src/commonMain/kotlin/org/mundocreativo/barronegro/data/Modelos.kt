package org.mundocreativo.barronegro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Sala(
    val id: String,
    val nombre: String,
    val descripcion: String? = null,
    @SerialName("zona_horaria") val zonaHoraria: String = "America/Bogota",
    @SerialName("calendario_abierto") val calendarioAbierto: Boolean = true,
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
    @SerialName("horario_id") val horarioId: String,
    @SerialName("sala_id") val salaId: String? = null,
    @SerialName("usuario_id") val usuarioId: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val estado: String,
    @SerialName("comentario_usuario") val comentarioUsuario: String? = null,
    @SerialName("comentario_admin") val comentarioAdmin: String? = null,
    @SerialName("propuesta_fecha") val propuestaFecha: String? = null,
    @SerialName("propuesta_hora_inicio") val propuestaHoraInicio: String? = null,
    @SerialName("propuesta_hora_fin") val propuestaHoraFin: String? = null,
    @SerialName("creado_en") val creadoEn: String? = null,
)

@Serializable
data class Bloqueo(
    val id: String,
    @SerialName("sala_id") val salaId: String,
    val desde: String,
    val hasta: String,
    val motivo: String? = null,
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
