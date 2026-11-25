package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mundocreativo.barronegro.EstadoApp
import org.mundocreativo.barronegro.Formato
import org.mundocreativo.barronegro.data.FilaReporte
import org.mundocreativo.barronegro.data.ReglaDisponibilidad
import org.mundocreativo.barronegro.data.Reserva
import org.mundocreativo.barronegro.data.SupabaseConfig

@Composable
fun PantallaAdmin(estado: EstadoApp) {
    var fechas by remember { mutableStateOf("") }
    var inicio by remember { mutableStateOf("09:00") }
    var fin by remember { mutableStateOf("11:00") }
    var capacidad by remember { mutableStateOf("1") }

    var bloqueoDesde by remember { mutableStateOf("") }
    var bloqueoHasta by remember { mutableStateOf("") }
    var bloqueoMotivo by remember { mutableStateOf("") }

    var rechazando by remember { mutableStateOf<Reserva?>(null) }
    var proponiendo by remember { mutableStateOf<Reserva?>(null) }
    var cancelando by remember { mutableStateOf<FilaReporte?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Aviso("Panel del administrador de ${SupabaseConfig.NOMBRE_SALA}. Los cambios afectan solo a esta sala.")

        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Calendario del taller", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (estado.calendarioAbierto) "Abierto: los usuarios pueden solicitar franjas"
                        else "Cerrado: no se aceptan solicitudes nuevas",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = estado.calendarioAbierto,
                    onCheckedChange = { estado.cambiarCalendario(it) },
                )
            }
        }

        HorarioSemanal(estado)
        PoliticaReservas(estado)

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Franjas puntuales (opcional)", fontWeight = FontWeight.SemiBold)
                Text(
                    "El horario semanal ya genera las franjas de todos los días. Usa esto solo para " +
                            "publicar una fecha fuera de ese horario.",
                    style = MaterialTheme.typography.bodySmall,
                )
                CampoTextoAmplio(
                    valor = fechas,
                    alCambiar = { fechas = it },
                    etiqueta = "Fechas (AAAA-MM-DD)",
                    ayuda = "Separa varias fechas con comas: 2026-12-01, 2026-12-03",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(inicio, { inicio = it }, "Inicio", Modifier.weight(1f))
                    CampoTexto(fin, { fin = it }, "Fin", Modifier.weight(1f))
                }
                CampoTexto(
                    valor = capacidad,
                    alCambiar = { nuevo -> capacidad = nuevo.filter { it.isDigit() } },
                    etiqueta = "Cupos por franja",
                    ayuda = "1 significa una reserva por franja",
                )
                Button(
                    onClick = {
                        val lista = fechas
                            .split(',', ' ', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        estado.crearHorarios(lista, inicio, fin, capacidad.toIntOrNull() ?: 1)
                        fechas = ""
                    },
                    enabled = fechas.isNotBlank() && !estado.trabajando,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Crear horarios") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Bloquear el calendario", fontWeight = FontWeight.SemiBold)
                CampoTexto(
                    bloqueoDesde, { bloqueoDesde = it }, "Desde",
                    ayuda = "Formato: 2026-12-24T00:00:00-05:00",
                )
                CampoTexto(
                    bloqueoHasta, { bloqueoHasta = it }, "Hasta",
                    ayuda = "Formato: 2027-01-02T00:00:00-05:00",
                )
                CampoTexto(bloqueoMotivo, { bloqueoMotivo = it }, "Motivo (opcional)")
                Button(
                    onClick = {
                        estado.bloquearCalendario(
                            bloqueoDesde.trim(),
                            bloqueoHasta.trim(),
                            bloqueoMotivo.trim().ifBlank { null },
                        )
                        bloqueoDesde = ""
                        bloqueoHasta = ""
                        bloqueoMotivo = ""
                    },
                    enabled = bloqueoDesde.isNotBlank() && bloqueoHasta.isNotBlank() && !estado.trabajando,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Bloquear rango") }

                estado.bloqueos.forEach { bloqueo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${Formato.momentoCorto(bloqueo.desde)} → ${Formato.momentoCorto(bloqueo.hasta)}" +
                                    (bloqueo.motivo?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { estado.quitarBloqueo(bloqueo.id) }) { Text("Quitar") }
                    }
                }
            }
        }

        Text("Solicitudes por resolver", style = MaterialTheme.typography.titleSmall)
        if (estado.pendientes.isEmpty()) {
            Text("No hay solicitudes pendientes.", style = MaterialTheme.typography.bodySmall)
        }
        estado.pendientes.forEach { reserva ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Formato.fechaLarga(reserva.fecha) + " · " +
                                    Formato.rangoHora(reserva.horaInicio, reserva.horaFin),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        EtiquetaEstado(reserva.estado)
                    }
                    reserva.comentarioUsuario?.takeIf { it.isNotBlank() }?.let {
                        Text("Comentario del usuario: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    val contacto = listOfNotNull(
                        reserva.nombreContacto?.takeIf { it.isNotBlank() },
                        reserva.correoContacto?.takeIf { it.isNotBlank() },
                        reserva.telefonoContacto?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (contacto.isNotBlank()) {
                        Text(contacto, style = MaterialTheme.typography.bodySmall)
                    }
                    if (reserva.esAnonima) {
                        Text("Reserva anónima", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { estado.aprobarReserva(reserva.id) }) { Text("Aprobar") }
                        if (!reserva.esAnonima) {
                            OutlinedButton(onClick = { proponiendo = reserva }) { Text("Proponer otra fecha") }
                        }
                        TextButton(onClick = { rechazando = reserva }) { Text("Rechazar") }
                    }
                }
            }
        }

        Text("Calendario y reportes del mes", style = MaterialTheme.typography.titleSmall)
        SeccionReportes(estado) { fila -> cancelando = fila }
    }

    rechazando?.let { reserva ->
        DialogoRechazo(reserva, alCerrar = { rechazando = null }) { motivo ->
            estado.rechazarReserva(reserva.id, motivo)
            rechazando = null
        }
    }

    proponiendo?.let { reserva ->
        DialogoPropuesta(reserva, alCerrar = { proponiendo = null }) { fecha, horaInicio, horaFin, motivo ->
            estado.proponerReprogramacion(reserva.id, fecha, horaInicio, horaFin, motivo)
            proponiendo = null
        }
    }

    cancelando?.let { fila ->
        DialogoCancelarAgenda(
            fila = fila,
            alCerrar = { cancelando = null },
            alConfirmar = { motivo ->
                estado.cancelarReservaAdmin(fila.reservaId, motivo)
                cancelando = null
            },
        )
    }
}

@Composable
private fun DialogoCancelarAgenda(
    fila: FilaReporte,
    alCerrar: () -> Unit,
    alConfirmar: (String?) -> Unit,
) {
    var motivo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Cancelar reserva") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    Formato.fechaLarga(fila.fecha) + " · " +
                            Formato.rangoHora(fila.horaInicio, fila.horaFin),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (!fila.correo.isNullOrBlank()) {
                        "Se enviará un correo de cancelación a ${fila.correo}."
                    } else {
                        "Esta reserva no tiene correo de contacto; no se enviará aviso automático."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                CampoTexto(motivo, { motivo = it }, "Motivo (opcional)")
            }
        },
        confirmButton = {
            TextButton(onClick = { alConfirmar(motivo.trim().ifBlank { null }) }) {
                Text("Cancelar y avisar")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Volver") } },
    )
}

@Composable
private fun DialogoRechazo(
    reserva: Reserva,
    alCerrar: () -> Unit,
    alConfirmar: (String) -> Unit,
) {
    var motivo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Rechazar solicitud") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${Formato.fechaLarga(reserva.fecha)} · " +
                            Formato.rangoHora(reserva.horaInicio, reserva.horaFin),
                    style = MaterialTheme.typography.bodySmall,
                )
                CampoTexto(motivo, { motivo = it }, "Motivo del rechazo")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alConfirmar(motivo.trim().ifBlank { "No disponible" }) },
            ) { Text("Rechazar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoPropuesta(
    reserva: Reserva,
    alCerrar: () -> Unit,
    alConfirmar: (String, String, String, String?) -> Unit,
) {
    var fecha by remember { mutableStateOf("") }
    var horaInicio by remember { mutableStateOf("") }
    var horaFin by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Proponer nueva fecha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Solicitud original: ${Formato.fechaLarga(reserva.fecha)} · " +
                            Formato.rangoHora(reserva.horaInicio, reserva.horaFin),
                    style = MaterialTheme.typography.bodySmall,
                )
                CampoTexto(fecha, { fecha = it }, "Nueva fecha", ayuda = "AAAA-MM-DD")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(horaInicio, { horaInicio = it }, "Inicio", Modifier.weight(1f), ayuda = "HH:MM")
                    CampoTexto(horaFin, { horaFin = it }, "Fin", Modifier.weight(1f), ayuda = "HH:MM")
                }
                CampoTexto(motivo, { motivo = it }, "Motivo (opcional)")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alConfirmar(
                        fecha.trim(),
                        horaInicio.trim(),
                        horaFin.trim(),
                        motivo.trim().ifBlank { null },
                    )
                },
                enabled = fecha.isNotBlank() && horaInicio.isNotBlank() && horaFin.isNotBlank(),
            ) { Text("Enviar propuesta") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

private data class DiaHorario(
    val dia: Int,
    val activo: Boolean,
    val inicio: String,
    val fin: String,
)

/** Editor de la plantilla semanal: una fila por día, guardada en una sola llamada. */
@Composable
private fun HorarioSemanal(estado: EstadoApp) {
    var filas by remember { mutableStateOf<List<DiaHorario>>(emptyList()) }
    var sembrado by remember { mutableStateOf(false) }
    val firma = estado.reglas.joinToString(";") {
        "${it.diaSemana}:${it.horaInicio.take(5)}-${it.horaFin.take(5)}"
    }

    // Se siembra con lo publicado; la primera vez, si aún no hay nada, se propone
    // lunes a viernes de 09:00 a 18:00 (sin guardar hasta que el administrador acepte).
    LaunchedEffect(firma) {
        val primeraVez = !sembrado
        filas = (1..7).map { dia ->
            val regla = estado.reglas.firstOrNull { it.diaSemana == dia }
            DiaHorario(
                dia = dia,
                activo = regla != null || (primeraVez && dia <= 5),
                inicio = regla?.horaInicio?.take(5) ?: "09:00",
                fin = regla?.horaFin?.take(5) ?: "18:00",
            )
        }
        sembrado = true
    }

    fun actualizar(dia: Int, cambio: (DiaHorario) -> DiaHorario) {
        filas = filas.map { if (it.dia == dia) cambio(it) else it }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Horario semanal", fontWeight = FontWeight.SemiBold)
            Text(
                "Con estas horas se generan las franjas de todos los días; los usuarios reservan " +
                        "directamente sobre ellas. Formato HH:MM.",
                style = MaterialTheme.typography.bodySmall,
            )

            filas.forEach { fila ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Switch(
                        checked = fila.activo,
                        onCheckedChange = { activo ->
                            actualizar(fila.dia) { it.copy(activo = activo) }
                        },
                    )
                    Text(
                        Formato.nombreDiaSemana(fila.dia),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(80.dp),
                    )
                    CampoHora(
                        valor = fila.inicio,
                        alCambiar = { nuevo -> actualizar(fila.dia) { it.copy(inicio = nuevo) } },
                        pista = "09:00",
                        modifier = Modifier.weight(1f),
                    )
                    Text("–", style = MaterialTheme.typography.bodySmall)
                    CampoHora(
                        valor = fila.fin,
                        alCambiar = { nuevo -> actualizar(fila.dia) { it.copy(fin = nuevo) } },
                        pista = "18:00",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            TextButton(
                onClick = {
                    val lunes = filas.firstOrNull { it.dia == 1 }
                    if (lunes != null) {
                        filas = filas.map {
                            if (it.dia in 1..5) {
                                it.copy(activo = true, inicio = lunes.inicio, fin = lunes.fin)
                            } else {
                                it
                            }
                        }
                    }
                },
            ) { Text("Copiar lunes a lunes–viernes") }

            Button(
                onClick = {
                    val reglas = filas
                        .filter { it.activo && it.inicio.isNotBlank() && it.fin.isNotBlank() }
                        .map { ReglaDisponibilidad(it.dia, it.inicio.trim(), it.fin.trim()) }
                    estado.guardarDisponibilidad(reglas)
                },
                enabled = !estado.trabajando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar horario semanal") }
        }
    }
}

/** Política de reservas de la sala: duración, buffer, antelación, cupos y modo de aprobación. */
@Composable
private fun PoliticaReservas(estado: EstadoApp) {
    val sala = estado.sala
    val firma = sala?.let {
        "${it.duracionTurnoMin},${it.duracionMaximaMin},${it.bufferMin},${it.antelacionMinHoras}," +
                "${it.diasMaxAntelacion},${it.capacidadPorTurno},${it.aprobacionAutomatica}," +
                "${it.maxReservasPorDia}"
    }

    var turno by remember { mutableStateOf("60") }
    var maxima by remember { mutableStateOf("120") }
    var buffer by remember { mutableStateOf("0") }
    var antelacion by remember { mutableStateOf("0") }
    var horizonte by remember { mutableStateOf("60") }
    var capacidad by remember { mutableStateOf("1") }
    var porDia by remember { mutableStateOf("1") }
    var automatica by remember { mutableStateOf(true) }

    LaunchedEffect(firma) {
        val actual = sala ?: return@LaunchedEffect
        turno = actual.duracionTurnoMin.toString()
        maxima = actual.duracionMaximaMin.toString()
        buffer = actual.bufferMin.toString()
        antelacion = actual.antelacionMinHoras.toString()
        horizonte = actual.diasMaxAntelacion.toString()
        capacidad = actual.capacidadPorTurno.toString()
        porDia = actual.maxReservasPorDia.toString()
        automatica = actual.aprobacionAutomatica
    }

    fun soloDigitos(texto: String): String = texto.filter { it.isDigit() }.take(3)

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Política de reservas", fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(
                    turno, { turno = soloDigitos(it) }, "Turno (min)",
                    Modifier.weight(1f), ayuda = "Paso entre horas",
                )
                CampoTexto(
                    maxima, { maxima = soloDigitos(it) }, "Máximo (min)",
                    Modifier.weight(1f), ayuda = "Duración tope",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(
                    buffer, { buffer = soloDigitos(it) }, "Buffer (min)",
                    Modifier.weight(1f), ayuda = "Pausa entre reservas",
                )
                CampoTexto(
                    antelacion, { antelacion = soloDigitos(it) }, "Antelación (h)",
                    Modifier.weight(1f), ayuda = "Mínimo para reservar",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(
                    horizonte, { horizonte = soloDigitos(it) }, "Días a la vista",
                    Modifier.weight(1f), ayuda = "Hasta cuándo se reserva",
                )
                CampoTexto(
                    capacidad, { capacidad = soloDigitos(it) }, "Cupos por franja",
                    Modifier.weight(1f),
                )
            }
            CampoTexto(
                porDia, { porDia = soloDigitos(it) }, "Reservas por persona y día",
                ayuda = "0 = sin límite",
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aprobación automática", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (automatica) "La reserva queda confirmada al instante"
                        else "Cada solicitud queda pendiente de revisión",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = automatica, onCheckedChange = { automatica = it })
            }

            Button(
                onClick = {
                    estado.guardarPolitica(
                        duracionTurnoMin = turno.toIntOrNull() ?: 60,
                        duracionMaximaMin = maxima.toIntOrNull() ?: 120,
                        bufferMin = buffer.toIntOrNull() ?: 0,
                        antelacionMinHoras = antelacion.toIntOrNull() ?: 0,
                        diasMaxAntelacion = horizonte.toIntOrNull() ?: 60,
                        capacidadPorTurno = capacidad.toIntOrNull() ?: 1,
                        aprobacionAutomatica = automatica,
                        maxReservasPorDia = porDia.toIntOrNull() ?: 1,
                    )
                },
                enabled = !estado.trabajando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar política") }
        }
    }
}

