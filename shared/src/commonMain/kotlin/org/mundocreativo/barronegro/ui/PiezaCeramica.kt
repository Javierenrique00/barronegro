package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun colorDesdeHex(hex: String): Color {
    val limpio = hex.removePrefix("#")
    val valor = runCatching {
        when (limpio.length) {
            6 -> 0xFF000000L or limpio.toLong(16)
            8 -> limpio.toLong(16)
            else -> null
        }
    }.getOrNull()
    return if (valor == null) Color(0xFF8B4A2F) else Color(valor)
}

/** Dibuja una pieza de cerámica con forma, color y número de franjas. */
@Composable
fun PiezaCeramica(
    forma: String,
    franjas: Int,
    color: Color,
    rotacion: Int = 0,
    tamano: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(tamano)) {
        val ancho = size.width
        val alto = size.height
        val camino = caminoForma(forma, ancho, alto)
        rotate(rotacion.toFloat()) {
            drawPath(camino, color = color.copy(alpha = 0.16f))
            drawPath(camino, color = color, style = Stroke(width = ancho * 0.05f))
            clipPath(camino) {
                val total = franjas.coerceIn(0, 6)
                if (total > 0) {
                    for (indice in 1..total) {
                        val y = alto * (0.2f + indice * (0.6f / (total + 1)))
                        drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(ancho, y),
                            strokeWidth = alto * 0.05f,
                        )
                    }
                }
            }
        }
    }
}

private fun caminoForma(forma: String, w: Float, h: Float): Path = when (forma) {
    "plato" -> Path().apply {
        addOval(Rect(Offset(w * 0.06f, h * 0.3f), Size(w * 0.88f, h * 0.42f)))
    }

    "taza" -> Path().apply {
        addRoundRect(
            RoundRect(
                Rect(Offset(w * 0.08f, h * 0.24f), Size(w * 0.6f, h * 0.52f)),
                w * 0.08f,
                w * 0.08f,
            ),
        )
        addOval(Rect(Offset(w * 0.62f, h * 0.36f), Size(w * 0.26f, h * 0.26f)))
    }

    "jarron" -> Path().apply {
        moveTo(w * 0.38f, h * 0.1f)
        lineTo(w * 0.62f, h * 0.1f)
        lineTo(w * 0.62f, h * 0.28f)
        cubicTo(w * 0.98f, h * 0.45f, w * 0.86f, h * 0.86f, w * 0.68f, h * 0.9f)
        lineTo(w * 0.32f, h * 0.9f)
        cubicTo(w * 0.14f, h * 0.86f, w * 0.02f, h * 0.45f, w * 0.38f, h * 0.28f)
        close()
    }

    else -> Path().apply {
        moveTo(w * 0.2f, h * 0.24f)
        lineTo(w * 0.8f, h * 0.24f)
        lineTo(w * 0.7f, h * 0.88f)
        lineTo(w * 0.3f, h * 0.88f)
        close()
    }
}
