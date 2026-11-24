package org.mundocreativo.barronegro.data

/** Nombre de la cookie que identifica al visitante anónimo en la web. */
const val NOMBRE_COOKIE_SESION = "barronegro_sesion"

/**
 * Lee la sesión anónima guardada en este dispositivo.
 *
 * En web es una cookie (`document.cookie`); en Android, `SharedPreferences`. La cookie
 * solo contiene el token opaco emitido por Postgres, nunca datos personales.
 */
expect fun leerSesionAnonima(): String?

/**
 * Guarda la sesión anónima. Devuelve `false` si el navegador bloqueó la cookie (por
 * ejemplo, en modo privado estricto). En Android siempre devuelve `true`.
 */
expect fun guardarSesionAnonima(valor: String): Boolean

expect fun borrarSesionAnonima()
