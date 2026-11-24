package org.mundocreativo.barronegro

/** Formato de fechas y horas en español, sin dependencias de configuración regional. */
object Formato {

    private val dias = listOf(
        "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
    )
    private val meses = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** 1 = lunes … 7 = domingo (algoritmo de Sakamoto). */
    fun diaSemana(anio: Int, mes: Int, dia: Int): Int {
        val desplazamiento = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
        val ajustado = if (mes < 3) anio - 1 else anio
        val resultado = (ajustado + ajustado / 4 - ajustado / 100 + ajustado / 400 +
            desplazamiento[mes - 1] + dia) % 7
        return if (resultado == 0) 7 else resultado
    }

    fun fechaLarga(iso: String): String {
        val partes = partes(iso) ?: return iso
        val (anio, mes, dia) = partes
        return "${dias[diaSemana(anio, mes, dia) - 1]} $dia de ${meses[mes - 1]} de $anio"
    }

    fun fechaCorta(iso: String): String {
        val partes = partes(iso) ?: return iso
        val (anio, mes, dia) = partes
        return "${dias[diaSemana(anio, mes, dia) - 1].take(3)} $dia ${meses[mes - 1].take(3)}"
    }

    fun hora(valor: String): String = valor.take(5)

    fun rangoHora(inicio: String, fin: String): String = "${hora(inicio)} – ${hora(fin)}"

    /** Minutos desde la medianoche de una hora HH:MM[:SS]. */
    fun minutos(hora: String): Int {
        val trozos = hora.take(8).split(':')
        val h = trozos.getOrNull(0)?.toIntOrNull() ?: return 0
        val m = trozos.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    /** Suma minutos a una hora HH:MM y devuelve HH:MM (sin cruzar de día). */
    fun sumarMinutos(hora: String, cantidad: Int): String {
        val total = ((minutos(hora) + cantidad) % (24 * 60) + 24 * 60) % (24 * 60)
        return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
    }

    /** Duración legible: "30 min", "1 h", "1 h 30 min". */
    fun duracion(minutos: Int): String = when {
        minutos < 60 -> "$minutos min"
        minutos % 60 == 0 -> "${minutos / 60} h"
        else -> "${minutos / 60} h ${minutos % 60} min"
    }

    /** "Noviembre" a partir del número de mes (1 = enero). */
    fun nombreMes(mes: Int): String =
        meses[(mes - 1).coerceIn(0, 11)].replaceFirstChar { it.uppercase() }

    /** "Lunes" a partir de 1 = lunes … 7 = domingo. */
    fun nombreDiaSemana(indice: Int): String =
        dias[(indice - 1).coerceIn(0, 6)].replaceFirstChar { it.uppercase() }

    /** Inicial del día para la cabecera de la rejilla. */
    fun inicialDiaSemana(indice: Int): String =
        listOf("L", "M", "X", "J", "V", "S", "D")[(indice - 1).coerceIn(0, 6)]

    fun momentoCorto(iso: String): String {
        val fecha = iso.substringBefore('T')
        val hora = iso.substringAfter('T', "").take(5)
        val partes = partes(fecha) ?: return iso
        val (_, mes, dia) = partes
        return if (hora.isBlank()) "$dia ${meses[mes - 1].take(3)}" else "$dia ${meses[mes - 1].take(3)} · $hora"
    }

    fun etiquetaEstado(estado: String): String = when (estado) {
        "pendiente" -> "Pendiente"
        "aprobada" -> "Confirmada"
        "reprogramada" -> "Nueva propuesta"
        "rechazada" -> "Rechazada"
        "cancelada" -> "Cancelada"
        "expirada" -> "Expirada"
        else -> estado
    }

    private fun partes(iso: String): Triple<Int, Int, Int>? {
        val trozos = iso.substringBefore('T').split('-')
        if (trozos.size != 3) return null
        val anio = trozos[0].toIntOrNull() ?: return null
        val mes = trozos[1].toIntOrNull() ?: return null
        val dia = trozos[2].toIntOrNull() ?: return null
        if (mes !in 1..12) return null
        return Triple(anio, mes, dia)
    }
}
