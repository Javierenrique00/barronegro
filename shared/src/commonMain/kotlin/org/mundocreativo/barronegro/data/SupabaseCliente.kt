package org.mundocreativo.barronegro.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Cliente único de Supabase para Android, JS y Wasm.
 * La URL y la clave pública se generan desde local.properties en tiempo de compilación.
 */
val supabase: SupabaseClient by lazy {
    createSupabaseClient(SupabaseConfig.URL, SupabaseConfig.CLAVE_PUBLICA) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}
