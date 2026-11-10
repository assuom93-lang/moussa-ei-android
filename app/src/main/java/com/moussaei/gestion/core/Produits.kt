package com.moussaei.gestion.core

/**
 * Catalogue « Matériel » : chaque matériel mis dans le camion ou livré y est enregistré
 * automatiquement. Les doublons sont fusionnés : quantité (x10, 2 …), majuscules, accents
 * et pluriels sont ignorés pour reconnaître un produit.
 */
object Produits {

    private val QTE_FIN = Regex("""\s*[(\[]?\s*[x×*]\s*\d+([.,]\d+)?\s*(u|pcs?|pieces?|pièces?)?\s*[)\]]?\s*$""", RegexOption.IGNORE_CASE)
    private val QTE_FIN2 = Regex("""\s+\d+\s*(u|pcs?|pieces?|pièces?)\s*$""", RegexOption.IGNORE_CASE)
    private val QTE_DEBUT = Regex("""^\s*\d+\s*[x×*]?\s+(?=\D)""", RegexOption.IGNORE_CASE)

    /** « Bastaings x20 » → « Bastaings », « 2 machine de Champ » → « Machine de Champ ». */
    fun nomBase(nom: String): String {
        var n = nom.trim()
        n = QTE_FIN.replace(n, "")
        n = QTE_FIN2.replace(n, "")
        n = QTE_DEBUT.replace(n, "")
        n = n.trim(' ', '-', ',', ';', ':').replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return nom.trim()
        return n.replaceFirstChar { it.uppercaseChar() }
    }

    /** Clé de comparaison : sans quantité, sans accents, sans pluriels. */
    fun cle(nom: String): String =
        Gestion.normaliser(nomBase(nom))
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { m -> if (m.length > 3 && (m.endsWith("s") || m.endsWith("x"))) m.dropLast(1) else m }

    fun cles(p: Produit): Set<String> = setOf(cle(p.nom)) + p.alias

    fun trouver(d: AppData, nom: String): Produit? {
        val k = cle(nom)
        if (k.isEmpty()) return null
        return d.produits.firstOrNull { k in cles(it) }
    }

    fun parId(d: AppData, id: String): Produit? = d.produits.firstOrNull { it.id == id }

    /**
     * Ajoute les produits manquants (camion + historique) et retient le fournisseur habituel :
     * celui du camion (le plus récent), sinon celui de l'historique s'il correspond à une fiche.
     */
    fun synchroniser(d: AppData): AppData {
        val produits = d.produits.toMutableList()
        val index = HashMap<String, Int>()
        produits.forEachIndexed { i, p -> cles(p).forEach { index[it] = i } }
        var change = false

        fun assurer(nom: String, fournisseurId: String, remplacer: Boolean) {
            val k = cle(nom)
            if (k.isEmpty() || k in d.reglages.produitsMasques) return
            val i = index[k]
            if (i == null) {
                produits += Produit(Gestion.newId(), nomBase(nom), fournisseurId)
                index[k] = produits.size - 1
                change = true
            } else if (fournisseurId.isNotBlank() && produits[i].fournisseurId != fournisseurId &&
                (remplacer || produits[i].fournisseurId.isBlank()) &&
                // Autre agence de la même enseigne : rien ne change pour l'utilisateur.
                !memeEnseigne(d, produits[i].fournisseurId, fournisseurId)
            ) {
                produits[i] = produits[i].copy(fournisseurId = fournisseurId)
                change = true
            }
        }

        val parNom = HashMap<String, Fournisseur>()
        d.fournisseurs.forEach { parNom[Gestion.normaliser(it.nom)] = it }
        // L'historique garde le nom de l'enseigne : on le relie à une de ses agences.
        Gestion.enseignes(d).forEach { e ->
            Gestion.fournisseur(d, Gestion.representant(d, e))?.let { parNom.putIfAbsent(Gestion.normaliser(e), it) }
        }
        // Historique d'abord (plus ancien), puis le camion qui prime.
        d.archives.asReversed().forEach { a ->
            val f = parNom[Gestion.normaliser(a.fournisseurNom)]?.id ?: ""
            assurer(a.nom, f, remplacer = false)
        }
        d.materiels.forEach { m -> assurer(m.nom, m.fournisseurId, remplacer = true) }
        return if (change) d.copy(produits = produits) else d
    }

    private fun memeEnseigne(d: AppData, a: String, b: String): Boolean {
        val fa = Gestion.fournisseur(d, a) ?: return false
        val fb = Gestion.fournisseur(d, b) ?: return false
        return Gestion.memeEnseigne(Gestion.enseigneDe(fa), Gestion.enseigneDe(fb))
    }

    /** Retient le fournisseur d'un produit (le crée s'il n'existe pas encore). */
    fun retenir(d: AppData, nom: String, fournisseurId: String): AppData {
        val p = trouver(d, nom)
        return if (p != null) changerFournisseur(d, p.id, fournisseurId)
        else d.copy(
            produits = d.produits + Produit(Gestion.newId(), nomBase(nom), fournisseurId),
            reglages = d.reglages.copy(produitsMasques = d.reglages.produitsMasques - cle(nom)),
        )
    }

    fun tries(d: AppData): List<Produit> = d.produits.sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }

    fun modifier(d: AppData, id: String, nom: String, fournisseurId: String, note: String): AppData =
        d.copy(produits = d.produits.map { if (it.id == id) it.copy(nom = nom.trim(), fournisseurId = fournisseurId, note = note.trim()) else it })

    fun changerPhoto(d: AppData, id: String, photo: String): AppData =
        d.copy(produits = d.produits.map { if (it.id == id) it.copy(photo = photo) else it })

    fun changerFournisseur(d: AppData, id: String, fournisseurId: String): AppData =
        d.copy(produits = d.produits.map { if (it.id == id) it.copy(fournisseurId = fournisseurId) else it })

    /** Fusionne [sourceId] dans [cibleId] : le nom de la source devient un alias de la cible. */
    fun fusionner(d: AppData, sourceId: String, cibleId: String): AppData {
        val s = parId(d, sourceId) ?: return d
        val c = parId(d, cibleId) ?: return d
        if (s.id == c.id) return d
        val fusion = c.copy(
            alias = (c.alias + cles(s)).distinct().filter { it != cle(c.nom) },
            fournisseurId = c.fournisseurId.ifBlank { s.fournisseurId },
            photo = c.photo.ifBlank { s.photo },
            note = listOf(c.note, s.note).filter { it.isNotBlank() }.joinToString(" · "),
        )
        return d.copy(produits = d.produits.filterNot { it.id == s.id }.map { if (it.id == c.id) fusion else it })
    }

    /** Retire du catalogue ; ne revient pas tout seul (même s'il est dans l'historique). */
    fun supprimer(d: AppData, id: String): AppData {
        val p = parId(d, id) ?: return d
        return d.copy(
            produits = d.produits.filterNot { it.id == id },
            reglages = d.reglages.copy(produitsMasques = d.reglages.produitsMasques + cles(p)),
        )
    }

    /** Livraisons passées de ce produit (archives), plus récentes en premier. */
    fun historique(d: AppData, p: Produit): List<Archive> {
        val k = cles(p)
        return d.archives.filter { cle(it.nom) in k }
    }

    /** Matériels du camion correspondant à ce produit. */
    fun dansCamion(d: AppData, p: Produit): List<Materiel> {
        val k = cles(p)
        return d.materiels.filter { cle(it.nom) in k }
    }

    /** Produits dont le nom contient la saisie (ceux qui commencent par la saisie d'abord). */
    fun suggestions(d: AppData, saisie: String, max: Int = 5): List<Produit> {
        val q = Gestion.normaliser(saisie)
        if (q.length < 2) return emptyList()
        return d.produits
            .filter { Gestion.normaliser(it.nom).contains(q) && Gestion.normaliser(it.nom) != q }
            .sortedWith(compareBy<Produit> { if (Gestion.normaliser(it.nom).startsWith(q)) 0 else 1 }.thenComparator { a, b -> Gestion.compareNoms(a.nom, b.nom) })
            .take(max)
    }

    /** Photo du produit correspondant à un matériel du camion ("" si aucune). */
    fun photoDe(d: AppData, nomMateriel: String): String = trouver(d, nomMateriel)?.photo ?: ""
}
