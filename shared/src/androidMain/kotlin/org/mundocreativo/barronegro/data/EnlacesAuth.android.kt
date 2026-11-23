package org.mundocreativo.barronegro.data

import android.content.Intent
import io.github.jan.supabase.auth.handleDeeplinks

/**
 * Importa la sesión cuando la app se abre desde un enlace del correo
 * (`org.mundocreativo.barronegro://auth-callback`), tanto en frío como con la app ya abierta.
 *
 * Vive en `shared` para que `androidApp` no dependa del SDK de Supabase.
 */
fun manejarEnlaceAuth(intent: Intent) {
    supabase.handleDeeplinks(intent)
}
