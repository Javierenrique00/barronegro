package org.mundocreativo.barronegro

/**
 * Cálculo de fechas del calendario en Kotlin puro: se usa igual en Android, JS y Wasm
 * sin depender de la zona horaria ni de la configuración regional del equipo.
 *
 * Los algoritmos de conversión vienen de Howard Hinnant ("chrono-Compatible
 * Low-Level Date Algorithms") y funcionan con años bisiestos.
 */
data class MesCalendario(val anio: Int, val mes: Int) {

    /** Cantidad de días del mes. */
    fun diasEnMes(): Int = when (mes) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (esBisiesto(anio)) 29 else 28
        else -> 30
    }

    /** 1 = lunes … 7 = domingo del día 1; sirve para las celdas vacías iniciales. */
    fun primerDiaSemana(): Int = Formato.diaSemana(anio, mes, 1)

    /** Primer día del mes en formato AAAA-MM-DD. */
    fun primerDiaIso(): String = iso(anio, mes, 1)

    /** Último día del mes en formato AAAA-MM-DD. */
    fun ultimoDiaIso(): String = iso(anio, mes, diasEnMes())

    /** Suma (o resta) meses cruzando el cambio de año. */
    fun sumar(meses: Int): MesCalendario {
        val total = anio * 12 + (mes - 1) + meses
        return MesCalendario(total.floorDiv(12), total.mod(12) + 1)
    }

    /** `true` si este mes es anterior al mes de [otro]. */
    fun anteriorA(otro: MesCalendario): Boolean =
        anio * 12 + mes < otro.anio * 12 + otro.mes

    override fun toString(): String = "$anio-${mes.toString().padStart(2, '0')}"

    companion object {
        fun desdeIso(iso: String): MesCalendario? {
            val trozos = iso.substringBefore('T').split('-')
            if (trozos.size != 3) return null
            val anio = trozos[0].toIntOrNull() ?: return null
            val mes = trozos[1].toIntOrNull() ?: return null
            if (mes !in 1..12) return null
            return MesCalendario(anio, mes)
        }

        fun actual(): MesCalendario =
            desdeIso(fechaHoyIso()) ?: MesCalendario(2026, 1)
    }
}

/** `true` si el año es bisiesto (regla gregoriana). */
fun esBisiesto(anio: Int): Boolean =
    (anio % 4 == 0 && anio % 100 != 0) || anio % 400 == 0

/** Suma (o resta) días a una fecha AAAA-MM-DD. Si no se puede leer, devuelve la entrada. */
fun sumarDias(iso: String, dias: Int): String {
    val (anio, mes, dia) = partirIso(iso) ?: return iso
    val (a, m, d) = civilDesdeDias(diasDesdeCivil(anio, mes, dia) + dias)
    return iso(a, m, d)
}

/** Días transcurridos entre dos fechas AAAA-MM-DD (b − a). */
fun diasEntre(desde: String, hasta: String): Int? {
    val a = partirIso(desde) ?: return null
    val b = partirIso(hasta) ?: return null
    return diasDesdeCivil(b.first, b.second, b.third) -
        diasDesdeCivil(a.first, a.second, a.third)
}

/** Construye AAAA-MM-DD. */
fun iso(anio: Int, mes: Int, dia: Int): String =
    "$anio-${mes.toString().padStart(2, '0')}-${dia.toString().padStart(2, '0')}"

private fun partirIso(iso: String): Triple<Int, Int, Int>? {
    val trozos = iso.substringBefore('T').split('-')
    if (trozos.size != 3) return null
    val anio = trozos[0].toIntOrNull() ?: return null
    val mes = trozos[1].toIntOrNull() ?: return null
    val dia = trozos[2].toIntOrNull() ?: return null
    if (mes !in 1..12) return null
    return Triple(anio, mes, dia)
}

// --- Algoritmos days-from-civil / civil-from-days (Howard Hinnant) -------------

private fun diasDesdeCivil(anio: Int, mes: Int, dia: Int): Int {
    val a = if (mes <= 2) anio - 1 else anio
    val era = if (a >= 0) a / 400 else (a - 399) / 400
    val anioDeEra = a - era * 400
    val mesAjustado = (mes + 9) % 12
    val diaDelAnio = (153 * mesAjustado + 2) / 5 + dia - 1
    val diaDeEra = anioDeEra * 365 + anioDeEra / 4 - anioDeEra / 100 + diaDelAnio
    return era * 146097 + diaDeEra - 719468
}

private fun civilDesdeDias(dias: Int): Triple<Int, Int, Int> {
    val z = dias + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val diaDeEra = z - era * 146097
    val anioDeEra = (diaDeEra - diaDeEra / 1460 + diaDeEra / 36524 - diaDeEra / 146096) / 365
    val anio = anioDeEra + era * 400
    val diaDelAnio = diaDeEra - (365 * anioDeEra + anioDeEra / 4 - anioDeEra / 100)
    val mesAjustado = (5 * diaDelAnio + 2) / 153
    val dia = diaDelAnio - (153 * mesAjustado + 2) / 5 + 1
    val mes = if (mesAjustado < 10) mesAjustado + 3 else mesAjustado - 9
    return Triple(if (mes <= 2) anio + 1 else anio, mes, dia)
}
