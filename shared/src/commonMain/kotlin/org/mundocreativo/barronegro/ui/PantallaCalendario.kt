package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.mundocreativo.barronegro.data.FranjaDisponible
import org.mundocreativo.barronegro.fechaHoyIso
import org.mundocreativo.barronegro.iso

/**
 * Calendario tipo Calendly: se elige un día en la rejilla del mes y después una hora.
 * Las franjas llegan ya calculadas por Postgres (`franjas_disponibles`), incluidos
 * cupos, bloqueos, antelación y horizonte: el cliente solo las pinta.
 */
@Composable
fun PantallaCalendario(estado: EstadoApp) {
    var diaElegido by remember { mutableStateOf<String?>(null) }
    var franjaElegida by remember { mutableStateOf<FranjaDisponible?>(null) }

    val mes = estado.mesCalendario
    val porFecha = estado.franjas.groupBy { it.fecha }
    val misDias = estado.reservas
        .filter { it.estado == "pendiente" || it.estado == "aprobada" }
        .map { it.fecha }
        .toSet()

    // Al abrir el mes (o al cambiar de mes) se preselecciona el primer día con cupo.
    LaunchedEffect(mes, estado.franjas) {
        val disponibles = porFecha.keys.sorted()
        val elegido = diaElegido
        if (elegido == null || elegido !in disponibles) {
            diaElegido = disponibles.firstOrNull()
        }
    }

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

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CabeceraMes(mes, alCambiar = { estado.cambiarMes(it) })
                CabeceraDias()
                RejillaDias(
                    mes = mes,
                    porFecha = porFecha,
                    misDias = misDias,
                    elegido = diaElegido,
                    habilitado = estado.calendarioAbierto,
                    alElegir = { diaElegido = it },
                )
            }
        }

        val dia = diaElegido
        val franjasDelDia = dia?.let { porFecha[it] }.orEmpty()

        when {
            estado.franjas.isEmpty() && mes == MesCalendario.actual() -> {
                Text("Todavía no hay horarios publicados.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "El administrador del taller debe publicar el horario semanal para que puedas reservar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            estado.franjas.isEmpty() -> {
                Text(
                    "No hay disponibilidad publicada para este mes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            dia != null && franjasDelDia.isNotEmpty() -> {
                FranjasDelDia(dia, franjasDelDia, estado.calendarioAbierto) { franjaElegida = it }
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

    franjaElegida?.let { franja ->
        DialogoReserva(estado, franja) { franjaElegida = null }
    }
}
@Composable
private fun CabeceraMes(mes: MesCalendario, alCambiar: (Int) -> Unit) {
    val puedeRetroceder = !mes.sumar(-1).anteriorA(MesCalendario.actual())
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { alCambiar(-1) }, enabled = puedeRetroceder) { Text("‹") }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${Formato.nombreMes(mes.mes)} ${mes.anio}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("Toca un día para ver las horas", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { alCambiar(1) }) { Text("›") }
    }
}

@Composable
private fun CabeceraDias() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..7).forEach { indice ->
            Text(
                Formato.inicialDiaSemana(indice),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RejillaDias(
    mes: MesCalendario,
    porFecha: Map<String, List<FranjaDisponible>>,
    misDias: Set<String>,
    elegido: String?,
    habilitado: Boolean,
    alElegir: (String) -> Unit,
) {
    val hoy = fechaHoyIso()
    val total = mes.diasEnMes()
    val huecos = mes.primerDiaSemana() - 1
    val filas = (huecos + total + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(filas) { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { columna ->
                    val dia = fila * 7 + columna - huecos + 1
                    if (dia in 1..total) {
                        val isoDia = iso(mes.anio, mes.mes, dia)
                        val libres = porFecha[isoDia]?.sumOf { it.cuposLibres } ?: 0
                        CeldaDia(
                            dia = dia,
                            hoy = isoDia == hoy,
                            elegido = isoDia == elegido,
                            libres = libres,
                            conReserva = isoDia in misDias,
                            habilitada = habilitado,
                            modifier = Modifier.weight(1f),
                            alTocar = { alElegir(isoDia) },
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
private fun CeldaDia(
    dia: Int,
    hoy: Boolean,
    elegido: Boolean,
    libres: Int,
    conReserva: Boolean,
    habilitada: Boolean,
    modifier: Modifier,
    alTocar: () -> Unit,
) {
    val habilitado = habilitada && libres > 0
    val forma = RoundedCornerShape(10.dp)
    val fondo = when {
        elegido -> MaterialTheme.colorScheme.primary
        habilitado -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val colorTexto = when {
        elegido -> MaterialTheme.colorScheme.onPrimary
        habilitado -> MaterialTheme.colorScheme.onSurface
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
            .clickable(enabled = habilitado, onClick = alTocar)
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
        if (habilitado) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("$libres", style = MaterialTheme.typography.labelSmall, color = colorTexto)
                if (conReserva) {
                    Text("✓", style = MaterialTheme.typography.labelSmall, color = colorTexto)
                }
            }
        }
    }
}

@Composable
private fun FranjasDelDia(
    fecha: String,
    franjas: List<FranjaDisponible>,
    habilitado: Boolean,
    alElegir: (FranjaDisponible) -> Unit,
) {
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
            Text("Elige la hora de inicio", style = MaterialTheme.typography.bodySmall)

            franjas.sortedBy { it.horaInicio }.chunked(3).forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fila.forEach { franja ->
                        OutlinedButton(
                            onClick = { alElegir(franja) },
                            enabled = habilitado,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Formato.hora(franja.horaInicio))
                                if (franja.cuposLibres > 1) {
                                    Text(
                                        "${franja.cuposLibres} cupos",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DialogoReserva(
    estado: EstadoApp,
    franja: FranjaDisponible,
    alCerrar: () -> Unit,
) {
    val paso = estado.sala?.duracionTurnoMin ?: 60
    val maximo = estado.sala?.duracionMaximaMin ?: 120
    val automatica = estado.sala?.aprobacionAutomatica != false
    val opciones = remember(franja, paso, maximo) { franja.duraciones(paso, maximo) }
    var duracion by remember(franja) { mutableStateOf(opciones.firstOrNull() ?: paso) }
    var comentario by remember(franja) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (automatica) "Confirmar reserva" else "Solicitar reserva") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    Formato.fechaLarga(franja.fecha),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${Formato.hora(franja.horaInicio)} – " +
                        Formato.sumarMinutos(franja.horaInicio, duracion),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (franja.cuposLibres > 1) {
                    Text(
                        "Cupos disponibles: ${franja.cuposLibres}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (opciones.size > 1) {
                    Text("Duración", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        opciones.forEach { opcion ->
                            FilterChip(
                                selected = opcion == duracion,
                                onClick = { duracion = opcion },
                                label = { Text(Formato.duracion(opcion)) },
                            )
                        }
                    }
                }

                CampoTexto(comentario, { comentario = it }, "Comentario (opcional)")
                PruebaHumana { token ->
                    estado.reservar(franja, duracion, comentario, token) { alCerrar() }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

