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
import org.mundocreativo.barronegro.data.Horario

@Composable
fun PantallaCalendario(estado: EstadoApp) {
    var seleccionado by remember { mutableStateOf<Horario?>(null) }
    val grupos = estado.horarios.groupBy { it.fecha }.toList().sortedBy { it.first }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!estado.calendarioAbierto) {
            Aviso("El administrador ha cerrado el calendario del taller por ahora.")
        }

        if (grupos.isEmpty()) {
            Text("Todavía no hay franjas publicadas.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "El administrador del taller debe crear los horarios disponibles.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        grupos.forEach { (fecha, franjas) ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        Formato.fechaLarga(fecha),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    franjas.forEach { franja ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Formato.rangoHora(franja.horaInicio, franja.horaFin),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    when {
                                        !franja.disponible -> "No disponible"
                                        franja.capacidad > 1 -> "Cupos disponibles: ${franja.capacidad}"
                                        else -> "Una persona por franja"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(
                                onClick = { seleccionado = franja },
                                enabled = franja.disponible && estado.calendarioAbierto,
                            ) {
                                Text("Solicitar")
                            }
                        }
                    }
                }
            }
        }

        if (estado.bloqueos.isNotEmpty()) {
            Text("Bloqueos del calendario", style = MaterialTheme.typography.titleSmall)
            estado.bloqueos.forEach { bloqueo ->
                Text(
                    "• ${Formato.momentoCorto(bloqueo.desde)} → ${Formato.momentoCorto(bloqueo.hasta)}" +
                        (bloqueo.motivo?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    val franjaElegida = seleccionado
    if (franjaElegida != null) {
        DialogoSolicitud(estado, franjaElegida) { seleccionado = null }
    }
}

@Composable
private fun DialogoSolicitud(estado: EstadoApp, horario: Horario, alCerrar: () -> Unit) {
    var comentario by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Solicitar reserva") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${Formato.fechaLarga(horario.fecha)} · ${Formato.rangoHora(horario.horaInicio, horario.horaFin)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                CampoTexto(comentario, { comentario = it }, "Comentario (opcional)")
                PruebaHumana { token ->
                    estado.solicitar(horario, comentario, token) { alCerrar() }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
