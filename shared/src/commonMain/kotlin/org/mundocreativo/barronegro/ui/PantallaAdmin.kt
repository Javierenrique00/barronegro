package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Crear franjas disponibles", fontWeight = FontWeight.SemiBold)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { estado.aprobarReserva(reserva.id) }) { Text("Aprobar") }
                        OutlinedButton(onClick = { proponiendo = reserva }) { Text("Proponer otra fecha") }
                        TextButton(onClick = { rechazando = reserva }) { Text("Rechazar") }
                    }
                }
            }
        }
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
