package org.mundocreativo.barronegro.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/** Esquema del enlace profundo declarado en `AndroidManifest.xml`. */
const val ESQUEMA_ENLACE: String = "org.mundocreativo.barronegro"

/** Host del enlace profundo declarado en `AndroidManifest.xml`. */
const val HOST_ENLACE: String = "auth-callback"

/**
 * Cliente único de Supabase para Android, JS y Wasm.
 * La URL y la clave pública se generan desde local.properties en tiempo de compilación.
 */
val supabase: SupabaseClient by lazy {
    createSupabaseClient(SupabaseConfig.URL, SupabaseConfig.CLAVE_PUBLICA) {
        install(Auth) {
            // Sin scheme/host, auth-kt no usa enlaces profundos y el correo de confirmación
            // devuelve al Site URL en lugar de abrir la app. En web se ignora: auth-kt lee la
            // URL del navegador.
            scheme = ESQUEMA_ENLACE
            host = HOST_ENLACE
        }
        install(Postgrest)
        install(Storage)
    }
}
