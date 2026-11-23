package org.mundocreativo.barronegro

actual fun fechaHoyIso(): String = js("new Date().toISOString().slice(0, 10)")
