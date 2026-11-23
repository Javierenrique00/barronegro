import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(libs.compose.ui)
        }
    }
}

// ---------------------------------------------------------------------------
// Publicación en GitHub Pages: copia el bundle de producción de Wasm a
// build/pages, descarta los sourcemaps y añade .nojekyll. Lo consume el
// workflow .github/workflows/publicar-paginas.yml.
// ---------------------------------------------------------------------------
val directorioDistWasm = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
val directorioSitio = layout.buildDirectory.dir("pages")

tasks.register<Sync>("prepararSitioPages") {
    group = "distribution"
    description = "Prepara en build/pages el sitio estático listo para GitHub Pages."

    dependsOn("wasmJsBrowserDistribution")

    // El .nojekyll llega con los recursos de webMain (webApp/src/webMain/resources/.nojekyll).
    from(directorioDistWasm)
    into(directorioSitio)
    exclude("**/*.map")
}