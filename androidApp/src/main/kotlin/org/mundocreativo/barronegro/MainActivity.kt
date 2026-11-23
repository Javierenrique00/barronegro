package org.mundocreativo.barronegro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.mundocreativo.barronegro.data.manejarEnlaceAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }

        // Si la app se abrió desde el enlace del correo (confirmación o recuperación),
        // importa la sesión antes de que la interfaz termine de inicializarse.
        manejarEnlaceAuth(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // La actividad es singleTask: los enlaces con la app ya abierta llegan aquí.
        manejarEnlaceAuth(intent)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}