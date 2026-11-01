package com.moussaei.gestion.core

/** Nettoyage du texte reconnu sur une photo (repris de la v3.21). */
object Ocr {
    fun nettoyer(brut: String): String {
        var t = brut.trim()
        t = t.replace(Regex("[|}{\\[\\]<>~`_]"), "")
        t = t.replace(Regex("l(?=\\d)"), "1") // l devant un chiffre → 1
        return t.lines()
            .map { it.trim() }
            .filter { it.length > 1 }
            .filterNot { Regex("^[.\\-=*#]+$").matches(it) }
            .joinToString("\n")
    }
}
