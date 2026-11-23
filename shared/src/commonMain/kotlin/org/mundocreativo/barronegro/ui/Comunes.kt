package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mundocreativo.barronegro.Formato

@Composable
fun MensajeError(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            texto,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun MensajeAviso(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            texto,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun Aviso(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            texto,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun CajaCentrada(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { contenido() }
}

@Composable
fun EtiquetaEstado(estado: String) {
    val fondo = when (estado) {
        "aprobada" -> Color(0xFFD7EBD9)
        "pendiente" -> Color(0xFFFBE7C6)
        "reprogramada" -> Color(0xFFD8E4F5)
        "rechazada", "cancelada", "expirada" -> Color(0xFFF3D6D6)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = fondo, shape = RoundedCornerShape(50)) {
        Text(
            Formato.etiquetaEstado(estado),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        supportingText = ayuda?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CampoTextoAmplio(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    ayuda: String? = null,
    altoMinimo: Dp = 96.dp,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = false,
        supportingText = ayuda?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().heightIn(min = altoMinimo),
    )
}
