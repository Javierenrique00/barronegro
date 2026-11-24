package org.mundocreativo.barronegro.data

actual fun leerSesionAnonima(): String? = PreferenciasApp.sesionAnonima()

actual fun guardarSesionAnonima(valor: String): Boolean {
    if (valor.isBlank()) return false
    PreferenciasApp.recordarSesionAnonima(valor)
    return PreferenciasApp.sesionAnonima() == valor
}

actual fun borrarSesionAnonima() = PreferenciasApp.olvidarSesionAnonima()
