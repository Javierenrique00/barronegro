import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// ---------------------------------------------------------------------------
// Configuración de Supabase: se lee de local.properties (no versionado) o de
// las variables de entorno SUPABASE_URL / SUPABASE_ANON_KEY, y se genera un
// archivo Kotlin común para que Android, JS y Wasm compartan los mismos valores.
// ---------------------------------------------------------------------------
val archivoLocal = rootProject.file("local.properties")
val urlEntorno = providers.environmentVariable("SUPABASE_URL").orNull.orEmpty()
val claveEntorno = providers.environmentVariable("SUPABASE_ANON_KEY").orNull.orEmpty()
val directorioConfiguracion = layout.buildDirectory.dir("generated/supabase")

val generarConfiguracionSupabase = tasks.register("generarConfiguracionSupabase") {
    val rutaLocal = archivoLocal.absolutePath
    val urlDeEntorno = urlEntorno
    val claveDeEntorno = claveEntorno
    val salida = directorioConfiguracion.map { it.file("SupabaseConfig.kt") }

    inputs.property("urlEntorno", urlDeEntorno)
    inputs.property("claveEntorno", claveDeEntorno)
    if (archivoLocal.exists()) inputs.file(rutaLocal)
    outputs.file(salida)

    doLast {
        val propiedades = Properties()
        val archivo = File(rutaLocal)
        if (archivo.exists()) archivo.inputStream().use { propiedades.load(it) }

        val url = urlDeEntorno.ifBlank { propiedades.getProperty("supabase.url").orEmpty() }
        val clave = claveDeEntorno.ifBlank { propiedades.getProperty("supabase.anonKey").orEmpty() }
        require(url.startsWith("http")) {
            "Falta supabase.url en local.properties (o SUPABASE_URL en el entorno)"
        }
        require(clave.isNotBlank()) {
            "Falta supabase.anonKey en local.properties (o SUPABASE_ANON_KEY en el entorno)"
        }

        val destino = salida.get().asFile
        destino.parentFile.mkdirs()
        destino.writeText(
            """
            package org.mundocreativo.barronegro.data

            /** Generado por :shared:generarConfiguracionSupabase. No editar a mano. */
            object SupabaseConfig {
                const val URL: String = "$url"
                const val CLAVE_PUBLICA: String = "$clave"
                const val ID_SALA: String = "00000000-0000-4000-8000-000000000001"
                const val NOMBRE_SALA: String = "Taller de cerámica"
                const val ZONA_HORARIA: String = "America/Bogota"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    js {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    android {
        namespace = "org.mundocreativo.barronegro.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.storage)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.client.js)
        }
        wasmJsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.client.js)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
    sourceSets.getByName("commonMain").kotlin.srcDir(directorioConfiguracion)
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure { dependsOn(generarConfiguracionSupabase) }
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
