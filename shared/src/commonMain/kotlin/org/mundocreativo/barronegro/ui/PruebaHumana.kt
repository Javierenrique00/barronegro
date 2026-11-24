package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mundocreativo.barronegro.data.PiezaReto
import org.mundocreativo.barronegro.data.Repositorio
import org.mundocreativo.barronegro.data.RetoHumano
import org.mundocreativo.barronegro.mensajeDeError
import kotlin.time.TimeSource

/**
 * Reto humano compartido: idéntico en Android y en la web.
 * La respuesta se valida en Postgres, no aquí: este componente solo dibuja y envía la elección.
 */
@Composable
fun PruebaHumana(
    modifier: Modifier = Modifier,
    correo: String? = null,
    sesion: String? = null,
    onVerificado: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var reto by remember { mutableStateOf<RetoHumano?>(null) }
    var seleccion by remember { mutableStateOf<Int?>(null) }
    var errorLocal by remember { mutableStateOf<String?>(null) }
    var cargandoReto by remember { mutableStateOf(true) }
    var verificando by remember { mutableStateOf(false) }
    var marca by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }

    fun pedirReto() {
        errorLocal = null
        seleccion = null
        cargandoReto = true
        alcance.launch {
            try {
                reto = Repositorio.crearReto(correo, sesion)
                marca = TimeSource.Monotonic.markNow()
            } catch (t: Throwable) {
                errorLocal = mensajeDeError(t)
            } finally {
                cargandoReto = false
            }
        }
    }

    LaunchedEffect(correo, sesion) { pedirReto() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Verificación humana",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Para reservar necesitamos confirmar que eres una persona: toca la pieza del taller que coincide con el molde.",
            style = MaterialTheme.typography.bodySmall,
        )

        when {
            cargandoReto -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Preparando el reto…", style = MaterialTheme.typography.bodySmall)
            }

            reto == null -> {
                Text(
                    errorLocal ?: "No se pudo preparar el reto.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { pedirReto() }) { Text("Reintentar") }
            }

            else -> {
                val actual = reto ?: return@Column
                val colorMolde = MaterialTheme.colorScheme.primary

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Molde", style = MaterialTheme.typography.labelSmall)
                        PiezaCeramica(actual.molde.forma, actual.molde.franjas, colorMolde)
                    }
                    Text(
                        actual.instruccion,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    actual.piezas.forEach { pieza ->
                        TarjetaPieza(pieza, seleccion == pieza.id) { seleccion = pieza.id }
                    }
                }

                if (errorLocal != null) {
                    Text(
                        errorLocal ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { pedirReto() }) { Text("Probar con otro reto") }
                }

                Button(
                    onClick = {
                        val elegida = seleccion ?: return@Button
                        verificando = true
                        alcance.launch {
                            try {
                                val duracion = marca.elapsedNow().inWholeMilliseconds.toInt()
                                val token = Repositorio.verificarReto(
                                    actual.id,
                                    elegida,
                                    duracion,
                                    sesion,
                                )
                                onVerificado(token.token)
                            } catch (t: Throwable) {
                                errorLocal = mensajeDeError(t)
                                seleccion = null
                            } finally {
                                verificando = false
                            }
                        }
                    },
                    enabled = seleccion != null && !verificando,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (verificando) "Verificando…" else "Verificar y continuar")
                }
            }
        }
    }
}

@Composable
private fun TarjetaPieza(pieza: PiezaReto, seleccionada: Boolean, alTocar: () -> Unit) {
    val colorPieza = colorDesdeHex(pieza.color)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (seleccionada) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            )
            .border(
                width = if (seleccionada) 2.dp else 1.dp,
                color = if (seleccionada) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable { alTocar() }
            .padding(5.dp),
    ) {
        PiezaCeramica(pieza.forma, pieza.franjas, colorPieza, pieza.rotacion, tamano = 56.dp)
    }
}
