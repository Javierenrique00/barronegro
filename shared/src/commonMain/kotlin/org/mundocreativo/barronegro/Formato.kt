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

    fun momentoCorto(iso: String): String {
        val fecha = iso.substringBefore('T')
        val hora = iso.substringAfter('T', "").take(5)
        val partes = partes(fecha) ?: return iso
        val (_, mes, dia) = partes
        return if (hora.isBlank()) "$dia ${meses[mes - 1].take(3)}" else "$dia ${meses[mes - 1].take(3)} · $hora"
    }

    fun etiquetaEstado(estado: String): String = when (estado) {
        "pendiente" -> "Pendiente"
        "aprobada" -> "Aprobada"
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
