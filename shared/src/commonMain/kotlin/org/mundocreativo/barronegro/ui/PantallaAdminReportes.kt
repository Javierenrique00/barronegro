package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mundocreativo.barronegro.EstadoApp
import org.mundocreativo.barronegro.Formato
import org.mundocreativo.barronegro.MesCalendario
import org.mundocreativo.barronegro.data.FilaReporte
import org.mundocreativo.barronegro.fechaHoyIso
import org.mundocreativo.barronegro.iso

/**
 * Calendario mensual de citas y reporte CSV para el administrador.
 *
 * Solo pinta lo que devuelve `reporte_reservas(...)`; la duración, el nombre y los datos
 * de contacto vienen ya resueltos de Postgres. El CSV se genera en el dispositivo, sin
 * subir nada a un servicio externo.
 */
@Composable
fun SeccionReportes(estado: EstadoApp, alCancelar: (FilaReporte) -> Unit) {
    var incluirCanceladas by remember { mutableStateOf(false) }
    var diaElegido by remember { mutableStateOf<String?>(null) }

    val mes = estado.mesReporte
    val activas = estado.reporte.filter { it.activa }
    val visibles = if (incluirCanceladas) estado.reporte else activas
    val porFecha = visibles.groupBy { it.fecha }

    // Al abrir el panel (o cambiar de mes) se preselecciona el primer día con citas.
    LaunchedEffect(mes, estado.reporte) {
        val dias = estado.reporte.map { it.fecha }.distinct().sorted()
        val actual = diaElegido
        if (actual == null || actual !in dias) diaElegido = dias.firstOrNull()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { estado.cambiarMesReporte(-1) },
                    enabled = !estado.trabajando,
                ) { Text("‹") }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${Formato.nombreMes(mes.mes)} ${mes.anio}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(resumen(activas), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = { estado.cambiarMesReporte(1) },
                    enabled = !estado.trabajando,
                ) { Text("›") }
            }

            CabeceraDiasReporte()
            RejillaReporte(
                mes = mes,
                porFecha = porFecha,
                elegido = diaElegido,
                alElegir = { diaElegido = it },
            )

            val dia = diaElegido
            if (dia == null) {
                Text("No hay citas en este mes.", style = MaterialTheme.typography.bodySmall)
            } else {
                ListaDelDia(dia, porFecha[dia].orEmpty(), estado, alCancelar)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Incluir canceladas y rechazadas",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "El calendario y el CSV muestran solo citas programadas por defecto.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Switch(
                    checked = incluirCanceladas,
                    onCheckedChange = { incluirCanceladas = it },
                )
            }

            Button(
                onClick = { estado.exportarReporteCsv(mes, visibles) },
                enabled = !estado.trabajando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Descargar CSV del mes") }
            Text(
                "Incluye nombre, correo, teléfono, fecha, hora, duración y estado. " +
                        "Se abre con Excel o Google Sheets.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CabeceraDiasReporte() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..7).forEach { dia ->
            Text(
                Formato.inicialDiaSemana(dia),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RejillaReporte(
    mes: MesCalendario,
    porFecha: Map<String, List<FilaReporte>>,
    elegido: String?,
    alElegir: (String) -> Unit,
) {
    val totalCeldas = ((mes.primerDiaSemana() - 1) + mes.diasEnMes() + 6) / 7 * 7
    val hoy = fechaHoyIso()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until totalCeldas).chunked(7).forEach { semana ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                semana.forEach { indice ->
                    val numero = indice - mes.primerDiaSemana() + 2
                    if (numero in 1..mes.diasEnMes()) {
                        val fecha = iso(mes.anio, mes.mes, numero)
                        CeldaReporte(
                            dia = numero,
                            citas = porFecha[fecha]?.size ?: 0,
                            hoy = fecha == hoy,
                            elegido = fecha == elegido,
                            modifier = Modifier.weight(1f),
                            alTocar = { alElegir(fecha) },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CeldaReporte(
    dia: Int,
    citas: Int,
    hoy: Boolean,
    elegido: Boolean,
    modifier: Modifier,
    alTocar: () -> Unit,
) {
    val forma = RoundedCornerShape(10.dp)
    val fondo = when {
        elegido -> MaterialTheme.colorScheme.primary
        citas > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val colorTexto = when {
        elegido -> MaterialTheme.colorScheme.onPrimary
        citas > 0 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Column(
        modifier = modifier
            .height(48.dp)
            .clip(forma)
            .background(fondo)
            .border(
                width = if (hoy && !elegido) 1.5.dp else 1.dp,
                color = if (hoy && !elegido) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = forma,
            )
            .clickable(onClick = alTocar)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$dia",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (hoy || elegido) FontWeight.Bold else FontWeight.Normal,
            color = colorTexto,
        )
        if (citas > 0) {
            Text("$citas", style = MaterialTheme.typography.labelSmall, color = colorTexto)
        }
    }
}

@Composable
private fun ListaDelDia(
    fecha: String,
    filas: List<FilaReporte>,
    estado: EstadoApp,
    alCancelar: (FilaReporte) -> Unit,
) {
    Text(
        Formato.fechaLarga(fecha),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (filas.isEmpty()) {
        Text("Sin citas este día.", style = MaterialTheme.typography.bodySmall)
        return
    }

    filas.sortedBy { it.horaInicio }.forEach { fila ->
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Formato.rangoHora(fila.horaInicio, fila.horaFin) + " · " +
                                Formato.duracion(fila.duracionMin),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    EtiquetaEstado(fila.estado)
                }
                Text(
                    listOfNotNull(
                        fila.nombre.ifBlank { "Sin nombre" },
                        fila.correo?.takeIf { it.isNotBlank() },
                        fila.telefono?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (fila.esAnonima) {
                    Text(
                        "Reserva anónima · identificada por el navegador",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                fila.comentario?.takeIf { it.isNotBlank() }?.let {
                    Text("Comentario: $it", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (fila.activa) {
                        TextButton(onClick = { alCancelar(fila) }) { Text("Cancelar reserva") }
                    }
                    if (!fila.correo.isNullOrBlank()) {
                        TextButton(onClick = { estado.reenviarCorreoReserva(fila.reservaId) }) {
                            Text("Reenviar correo")
                        }
                    }
                }
            }
        }
    }
}

/** "12 confirmadas · 3 pendientes · 1 con propuesta" para la cabecera del mes. */
private fun resumen(filas: List<FilaReporte>): String {
    if (filas.isEmpty()) return "Sin citas programadas"
    val confirmadas = filas.count { it.estado == "aprobada" }
    val pendientes = filas.count { it.estado == "pendiente" }
    val reprogramadas = filas.count { it.estado == "reprogramada" }
    return listOfNotNull(
        "$confirmadas confirmadas".takeIf { confirmadas > 0 },
        "$pendientes pendientes".takeIf { pendientes > 0 },
        "$reprogramadas con propuesta".takeIf { reprogramadas > 0 },
    ).joinToString(" · ")
}
