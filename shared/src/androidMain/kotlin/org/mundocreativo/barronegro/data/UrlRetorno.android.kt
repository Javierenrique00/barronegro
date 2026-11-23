package org.mundocreativo.barronegro.data

/** En Android el enlace vuelve a la app mediante el intent-filter de `AndroidManifest.xml`. */
actual fun urlRetornoAuth(): String? = "$ESQUEMA_ENLACE://$HOST_ENLACE"
