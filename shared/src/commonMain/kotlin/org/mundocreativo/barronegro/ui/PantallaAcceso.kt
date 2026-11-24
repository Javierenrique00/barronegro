package org.mundocreativo.barronegro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.mundocreativo.barronegro.EstadoApp
import org.mundocreativo.barronegro.data.PreferenciasApp

@Composable
fun PantallaAcceso(estado: EstadoApp, alVolver: (() -> Unit)? = null) {
    var modoRegistro by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf(PreferenciasApp.correoRecordado().orEmpty()) }
    var contrasena by remember { mutableStateOf("") }
    var verContrasena by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        if (alVolver != null) {
            TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) {
                Text("← Volver al calendario")
            }
        }
        Text(
            "Taller de cerámica",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Reserva tu franja del taller",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))

        if (modoRegistro) {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre completo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it },
            label = { Text("Correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { verContrasena = !verContrasena }) {
                    Text(if (verContrasena) "Ocultar" else "Ver")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                if (modoRegistro) {
                    estado.registrar(nombre, correo, contrasena)
                } else {
                    estado.entrar(correo, contrasena)
                }
            },
            enabled = correo.isNotBlank() && contrasena.length >= 8 &&
                    (!modoRegistro || nombre.isNotBlank()) && !estado.trabajando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    estado.trabajando -> "Procesando…"
                    modoRegistro -> "Crear cuenta"
                    else -> "Entrar"
                },
            )
        }

        TextButton(onClick = {
            modoRegistro = !modoRegistro
            estado.error = null
            estado.aviso = null
        }) {
            Text(if (modoRegistro) "Ya tengo cuenta" else "Crear una cuenta nueva")
        }

        if (!modoRegistro) {
            TextButton(
                onClick = { estado.recuperarContrasena(correo) },
                enabled = correo.isNotBlank() && !estado.trabajando,
            ) {
                Text("¿Olvidaste tu contraseña?")
            }
        }

        if (modoRegistro) {
            Text(
                "Te enviaremos un correo de confirmación. Confírmalo antes de iniciar sesión.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        estado.error?.let { MensajeError(it) }
        estado.aviso?.let { MensajeAviso(it) }
    }
}
