package org.mundocreativo.barronegro

import java.time.LocalDate

actual fun fechaHoyIso(): String = LocalDate.now().toString()
