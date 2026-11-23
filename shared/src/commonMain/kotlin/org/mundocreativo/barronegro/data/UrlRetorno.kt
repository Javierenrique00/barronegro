package org.mundocreativo.barronegro.data

/**
 * URL a la que Supabase devuelve al usuario tras confirmar el correo o restablecer la contraseña.
 *
 * No se puede usar el valor por defecto del SDK: en web devuelve `window.location.origin`, que
 * pierde el subdirectorio de GitHub Pages (`/barronegro/`), y en Android depende de `AuthConfig`.
 */
expect fun urlRetornoAuth(): String?
