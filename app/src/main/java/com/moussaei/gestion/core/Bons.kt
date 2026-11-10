package com.moussaei.gestion.core

/**
 * « Lire un bon » : découpe un bon (texte OCR, PDF ou message) en lignes de matériel et
 * conseille où aller chercher chaque ligne : d'abord d'après les habitudes (fournisseur
 * habituel du produit), sinon d'après des mots-clés.
 */
object Bons {

    /** Règles par mots-clés : enseignes conseillées, dans l'ordre de préférence. */
    data class Regle(val motsCles: List<String>, val enseignes: List<String>)

    val REGLES = listOf(
        Regle(
            listOf("pvc", "coude", "reduction", "manchon", "evacuation", "raccord", "siphon", "culotte", "te pvc",
                "tube pvc", "regard", "colle pvc", "bouchon", "tampon", "descente", "gouttiere", "assainissement"),
            listOf("PUM"),
        ),
        Regle(
            listOf("ciment", "sable", "gravier", "gravillon", "parpaing", "platre", "placo", "ba13", "ba 13", "mortier",
                "chaux", "enduit", "brique", "hourdis", "laine de verre", "isolant", "rail", "montant", "beton sac",
                "sac beton", "colle carrelage", "cuvelage", "ragreage", "polyane", "plaque"),
            listOf("Point P", "Raboni"),
        ),
        Regle(
            listOf("betonniere", "nacelle", "echafaudage", "marteau piqueur", "piqueur", "groupe electrogene",
                "mini pelle", "minipelle", "compacteur", "plaque vibrante", "dumper", "chariot", "burineur",
                "carotteuse", "aspirateur chantier", "location"),
            listOf("Loxam", "Kiloutou"),
        ),
        Regle(
            listOf("fer", "treillis", "acier", "ferraille", "armature", "tor", "hea", "ipn", "upn", "cornieres", "corniere"),
            listOf("Acipar"),
        ),
    )

    private val LIGNE_INUTILE = Regex(
        """^(bon|bon de commande|commande|devis|facture|date|tel|tél|telephone|téléphone|total|ttc|ht|tva|client|adresse|chantier|livraison|merci|bonjour|cordialement)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val PRIX = Regex("""\d+[.,]\d{2}\s*€|€\s*\d+[.,]?\d*""")

    /** Lignes de matériel d'un bon (en-têtes, totaux, prix, téléphones retirés). */
    fun lignes(texte: String): List<String> =
        Ocr.nettoyer(texte).lines()
            .map { it.replace(PRIX, "").replace(Regex("^[-•*·>\\s]+"), "").trim().trim(',', ';') }
            .filter { l ->
                l.count { it.isLetter() } >= 2 &&
                    !LIGNE_INUTILE.containsMatchIn(Gestion.normaliser(l)) &&
                    !Regex("""^\+?[\d\s.]{8,}$""").matches(l) &&
                    !Regex("""^\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}""").containsMatchIn(l)
            }
            .distinct()

    enum class Raison { HABITUDE, MOT_CLE, AUCUN }

    data class Conseil(
        val raison: Raison,
        /** Fournisseur précis (habitude, ou unique agence de l'enseigne) ; "" sinon. */
        val fournisseurId: String = "",
        /** Enseigne conseillée (mot-clé) ; "" pour une habitude sans enseigne. */
        val enseigne: String = "",
        val motCle: String = "",
    )

    private fun contientMot(texte: String, mot: String): Boolean =
        Regex("(^|[^a-z0-9])" + Regex.escape(mot) + "($|[^a-z0-9])").containsMatchIn(texte)

    fun conseil(d: AppData, ligne: String): Conseil {
        Produits.trouver(d, ligne)?.let { p ->
            if (p.fournisseurId.isNotBlank() && d.fournisseurs.any { it.id == p.fournisseurId }) {
                val f = d.fournisseurs.first { it.id == p.fournisseurId }
                return Conseil(Raison.HABITUDE, f.id, Gestion.enseigneDe(f))
            }
        }
        val t = Gestion.normaliser(ligne)
        for (r in REGLES) {
            val mot = r.motsCles.firstOrNull { contientMot(t, it) } ?: continue
            // Préférer une enseigne que l'utilisateur a déjà dans ses fournisseurs.
            val enseigne = r.enseignes.firstOrNull { Gestion.agencesDe(d, it).isNotEmpty() } ?: r.enseignes.first()
            val agences = Gestion.agencesDe(d, enseigne)
            return Conseil(Raison.MOT_CLE, if (agences.size == 1) agences[0].id else "", enseigne, mot)
        }
        return Conseil(Raison.AUCUN)
    }

    /** Libellé court du conseil, ex. « PUM (mot-clé : coude) ». */
    fun libelle(d: AppData, c: Conseil): String = when (c.raison) {
        Raison.HABITUDE -> (Gestion.nomFournisseur(d, c.fournisseurId) ?: c.enseigne) + " (habitude)"
        Raison.MOT_CLE -> c.enseigne + " (mot-clé : " + c.motCle + ")"
        Raison.AUCUN -> "Pas de conseil"
    }

    /** Clé de regroupement d'un conseil : fournisseur précis, sinon enseigne. */
    fun destination(d: AppData, c: Conseil): String = when {
        c.fournisseurId.isNotBlank() -> Gestion.nomFournisseur(d, c.fournisseurId) ?: c.enseigne
        c.enseigne.isNotBlank() -> c.enseigne
        else -> "Sans conseil"
    }
}
