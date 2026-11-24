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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.mundocreativo.barronegro.data.ReservaAnonima

/**
 * Reservas hechas desde este navegador. La identidad es la cookie de sesión; no hay
 * una cuenta detrás. Si se borra la cookie, esta lista deja de estar accesible.
 */
@Composable
fun PantallaReservasAnonimas(estado: EstadoApp) {
    var cancelando by remember { mutableStateOf<ReservaAnonima?>(null) }
    var motivo by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Aviso(
            "Estas reservas se reconocen por una cookie técnica de este navegador. " +
                    "Si borras las cookies, no podrás verlas aquí; conserva el correo de confirmación.",
        )

        if (estado.reservasAnonimas.isEmpty()) {
            Text("Aún no tienes reservas en este navegador.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Reserva una franja desde la pestaña Calendario. No necesitas crear una cuenta.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        estado.reservasAnonimas.forEach { reserva ->
            TarjetaReservaAnonima(
                reserva = reserva,
                alCancelar = {
                    motivo = ""
                    cancelando = reserva
                },
            )
        }

        if (estado.reservasAnonimas.isNotEmpty()) {
            OutlinedButton(
                onClick = { estado.olvidarNavegador() },
                enabled = !estado.trabajando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Olvidar este navegador")
            }
            Text(
                "Las reservas seguirán existiendo para el taller, pero este navegador dejará de " +
                        "reconocerlas. Úsalo solo en un dispositivo compartido.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    cancelando?.let { reserva ->
        AlertDialog(
            onDismissRequest = { cancelando = null },
            title = { Text("Cancelar reserva") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        Formato.fechaLarga(reserva.fecha) + " · " +
                                Formato.rangoHora(reserva.horaInicio, reserva.horaFin),
                    )
                    Text(
                        "El taller recibirá el aviso y se enviará un correo de cancelación a " +
                                "${reserva.correoContacto ?: "tu correo"}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    CampoTexto(motivo, { motivo = it }, "Motivo (opcional)")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        estado.cancelarReservaAnonima(reserva.reservaId, motivo.ifBlank { null })
                        cancelando = null
                    },
                ) {
                    Text("Cancelar reserva")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelando = null }) { Text("Volver") }
            },
        )
    }
}

@Composable
private fun TarjetaReservaAnonima(
    reserva: ReservaAnonima,
    alCancelar: () -> Unit,
) {
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
            reserva.nombreContacto?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            reserva.correoContacto?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            reserva.comentarioAdmin?.takeIf { it.isNotBlank() }?.let {
                Text("Nota: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (reserva.activa) {
                TextButton(onClick = alCancelar) { Text("Cancelar reserva") }
            }
        }
    }
}

