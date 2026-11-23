package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mundocreativo.barronegro.EstadoApp
import org.mundocreativo.barronegro.Formato
import org.mundocreativo.barronegro.data.Reserva

@Composable
fun PantallaReservas(estado: EstadoApp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (estado.reservas.isEmpty()) {
            Text("Aún no tienes reservas.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Solicita una franja desde la pestaña Calendario.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        estado.reservas.forEach { reserva ->
            TarjetaReserva(estado, reserva)
        }
    }
}

@Composable
private fun TarjetaReserva(estado: EstadoApp, reserva: Reserva) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Formato.fechaLarga(reserva.fecha),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                EtiquetaEstado(reserva.estado)
            }

            Text(
                Formato.rangoHora(reserva.horaInicio, reserva.horaFin),
                style = MaterialTheme.typography.bodyMedium,
            )

            reserva.comentarioUsuario?.takeIf { it.isNotBlank() }?.let {
                Text("Tu comentario: $it", style = MaterialTheme.typography.bodySmall)
            }
            reserva.comentarioAdmin?.takeIf { it.isNotBlank() }?.let {
                Text("Administrador: $it", style = MaterialTheme.typography.bodySmall)
            }

            if (reserva.estado == "reprogramada") {
                Text(
                    "Propuesta del administrador: " +
                        (reserva.propuestaFecha?.let { Formato.fechaLarga(it) } ?: "—") +
                        " · " +
                        Formato.rangoHora(
                            reserva.propuestaHoraInicio ?: reserva.horaInicio,
                            reserva.propuestaHoraFin ?: reserva.horaFin,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { estado.responderPropuesta(reserva.id, true) }) {
                        Text("Aceptar propuesta")
                    }
                    OutlinedButton(onClick = { estado.responderPropuesta(reserva.id, false) }) {
                        Text("Rechazar")
                    }
                }
            }

            if (reserva.estado == "pendiente" || reserva.estado == "aprobada") {
                TextButton(onClick = { estado.cancelarReserva(reserva.id) }) {
                    Text("Cancelar reserva")
                }
            }
        }
    }
}
