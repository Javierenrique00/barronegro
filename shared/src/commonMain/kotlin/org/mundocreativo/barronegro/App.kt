package org.mundocreativo.barronegro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mundocreativo.barronegro.data.SupabaseConfig
import org.mundocreativo.barronegro.ui.CajaCentrada
import org.mundocreativo.barronegro.ui.MensajeAviso
import org.mundocreativo.barronegro.ui.MensajeError
import org.mundocreativo.barronegro.ui.PantallaAcceso
import org.mundocreativo.barronegro.ui.PantallaAdmin
import org.mundocreativo.barronegro.ui.PantallaCalendario
import org.mundocreativo.barronegro.ui.PantallaReservas

private val ColoresTaller = lightColorScheme(
    primary = Color(0xFF8A4B2A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DCCF),
    onPrimaryContainer = Color(0xFF33140A),
    secondary = Color(0xFF556B5D),
    secondaryContainer = Color(0xFFDCE8DF),
    onSecondaryContainer = Color(0xFF16251B),
    background = Color(0xFFFDF8F4),
    surface = Color(0xFFFFFBF8),
    surfaceVariant = Color(0xFFF0E4DC),
    outlineVariant = Color(0xFFD8C6BA),
)

@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = ColoresTaller) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val alcance = rememberCoroutineScope()
            val estado = remember { EstadoApp(alcance) }

            LaunchedEffect(Unit) { estado.iniciar() }

            Column(modifier = Modifier.fillMaxSize().safeContentPadding()) {
                when {
                    estado.cargando -> CajaCentrada { CircularProgressIndicator() }
                    !estado.conectado -> PantallaAcceso(estado)
                    else -> ContenidoPrincipal(estado)
                }
            }
        }
    }
}

@Composable
private fun ContenidoPrincipal(estado: EstadoApp) {
    var pestana by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Cabecera(estado)
        if (estado.trabajando) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Box(modifier = Modifier.weight(1f)) {
            when (pestana) {
                0 -> PantallaCalendario(estado)
                1 -> PantallaReservas(estado)
                else -> if (estado.esAdmin) PantallaAdmin(estado) else PantallaCalendario(estado)
            }
        }
        Banner(estado)
        NavigationBar {
            NavigationBarItem(
                selected = pestana == 0,
                onClick = { pestana = 0 },
                icon = { Text("◷") },
                label = { Text("Calendario") },
            )
            NavigationBarItem(
                selected = pestana == 1,
                onClick = { pestana = 1 },
                icon = { Text("✓") },
                label = { Text("Mis reservas") },
            )
            if (estado.esAdmin) {
                NavigationBarItem(
                    selected = pestana == 2,
                    onClick = { pestana = 2 },
                    icon = { Text("⚙") },
                    label = { Text("Administración") },
                )
            }
        }
    }
}

@Composable
private fun Cabecera(estado: EstadoApp) {
    val perfil = estado.perfil
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                SupabaseConfig.NOMBRE_SALA,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                perfil?.let { "${it.nombreVisible} · ${if (it.esAdmin) "Administrador" else "Usuario"}" }
                    ?: "",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextButton(onClick = { estado.recargar() }) { Text("Actualizar") }
        TextButton(onClick = { estado.salir() }) { Text("Salir") }
    }
}

@Composable
private fun Banner(estado: EstadoApp) {
    val error = estado.error
    val aviso = estado.aviso
    if (error == null && aviso == null) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { MensajeError(error) }
                TextButton(onClick = { estado.error = null }) { Text("Cerrar") }
            }
        }
        if (aviso != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { MensajeAviso(aviso) }
                TextButton(onClick = { estado.aviso = null }) { Text("Cerrar") }
            }
        }
    }
}
