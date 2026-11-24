package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import barronegro.shared.generated.resources.Res
import barronegro.shared.generated.resources.reserva_confirmada
import org.jetbrains.compose.resources.painterResource
import org.mundocreativo.barronegro.Formato
import org.mundocreativo.barronegro.data.ConfirmacionReserva

/** Pantalla de éxito después de reservar sin cuenta. */
@Composable
fun DialogoReservaConfirmada(
    confirmacion: ConfirmacionReserva,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = {
            Text(
                if (confirmacion.confirmada) "¡Reserva confirmada!" else "¡Solicitud recibida!",
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(Res.drawable.reserva_confirmada),
                    contentDescription = "Reserva realizada",
                    modifier = Modifier.height(150.dp),
                )
                Text(
                    Formato.fechaLarga(confirmacion.fecha),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    Formato.rangoHora(confirmacion.horaInicio, confirmacion.horaFin),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (confirmacion.codigo.isNotBlank()) {
                    Text(
                        "Código: ${confirmacion.codigo}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (confirmacion.correo.isNotBlank()) {
                    Text(
                        if (confirmacion.confirmada) {
                            "Enviamos la confirmación a ${confirmacion.correo}."
                        } else {
                            "Te avisaremos a ${confirmacion.correo} cuando el taller confirme la solicitud."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    "Puedes ver y gestionar esta reserva en «Mis reservas» desde este mismo navegador.",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) { Text("Listo") }
        },
    )
}
