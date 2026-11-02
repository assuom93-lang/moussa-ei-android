package com.moussaei.gestion.core

/**
 * Format CSV de moussa EI.
 *
 * C'est à la fois le format de sauvegarde (export / import / transfert) et le format
 * de stockage interne de l'appli. Il est compatible avec la v3.21 :
 *  - sections "## CHANTIERS", "## FOURNISSEURS", "## ARCHIVES", "## PALETTES", "## HEURES"
 *  - nouvelle section "## MATERIELS" (le camion), absente des exports v3.21.
 *
 * Contrairement à la v3.21, les champs entre guillemets peuvent contenir des retours
 * à la ligne (notes sur plusieurs lignes) sans être coupés à l'import.
 */
object Csv {

    data class Import(
        val data: AppData,
        /** Vrai si le texte contenait une section MATERIELS (export de l'appli native). */
        val contientMateriels: Boolean,
        /** Vrai si le texte contenait des réglages (export v4.2 et plus). */
        val contientReglages: Boolean = false,
    ) {
        val estVide: Boolean get() = data.chantiers.isEmpty() && data.fournisseurs.isEmpty()
    }

    // ---------- Export ----------

    fun escape(value: String?): String {
        val s = value ?: return ""
        return if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }

    private fun row(vararg fields: String?): String = fields.joinToString(",") { escape(it) }

    fun export(d: AppData): String {
        val l = ArrayList<String>()
        l += "## CHANTIERS"
        l += "id,nom,surnom,adresse,chef,tel,notes,statut,materiels"
        d.chantiers.forEach { c ->
            l += row(c.id, c.nom, c.surnom, c.adresse, c.chef, c.tel, c.notes, c.statut.ifBlank { STATUT_EN_COURS }, c.materiels.joinToString(";"))
        }
        l += ""
        l += "## FOURNISSEURS"
        l += "id,nom,adresse,enseigne"
        d.fournisseurs.forEach { f -> l += row(f.id, f.nom, f.adresse, f.enseigne) }
        l += ""
        l += "## ARCHIVES"
        l += "id,nom,fournisseur,chantier,dateLivraison"
        d.archives.forEach { a -> l += row(a.id, a.nom, a.fournisseurNom, a.chantiersNom, a.dateLivraison) }
        l += ""
        l += "## PALETTES"
        l += "id,nom,fournisseurId,note,enseigne"
        d.palettes.forEach { p -> l += row(p.id, p.nom, p.fournisseurId, p.note, p.enseigne) }
        l += ""
        l += "## HEURES"
        l += "date,debut,fin,cp,note"
        d.heures.keys.sorted().forEach { k ->
            val h = d.heures.getValue(k)
            l += row(k, h.debut, h.fin, if (h.cp) "1" else "0", h.note)
        }
        l += ""
        l += "## MATERIELS"
        l += "id,nom,fournisseurId,notes,recupere,dateRecupere,emplacement"
        d.materiels.forEach { m ->
            l += row(m.id, m.nom, m.fournisseurId, m.notes, if (m.recupere) "1" else "0", m.dateRecupere, m.emplacement)
        }
        l += ""
        l += "## TRAJET"
        l += "id,type,ref,nom,adresse"
        if (d.trajetDepart.isNotBlank()) l += row("depart", "depart", "", "Départ", d.trajetDepart)
        d.trajet.forEach { a -> l += row(a.id, a.type, a.ref, a.nom, a.adresse) }
        l += ""
        l += "## A_RENDRE"
        l += "id,enseigne,type,quantite,dateRecup,rendue,dateRendue,note"
        d.aRendre.forEach { p ->
            l += row(p.id, p.enseigne, p.type, p.quantite.toString(), p.dateRecup, if (p.rendue) "1" else "0", p.dateRendue, p.note)
        }
        l += ""
        l += "## REGLAGES"
        l += "id,valeur"
        val r = d.reglages
        l += row("entreprise", r.entreprise)
        l += row("depot", r.depot)
        l += row("finirAuDepot", if (r.finirAuDepot) "1" else "0")
        l += row("version", r.version.toString())
        l += row("produitsMasques", r.produitsMasques.sorted().joinToString("|"))
        l += ""
        l += "## PRODUITS"
        l += "id,nom,fournisseurId,photo,note,alias"
        d.produits.forEach { p -> l += row(p.id, p.nom, p.fournisseurId, p.photo, p.note, p.alias.joinToString("|")) }
        return l.joinToString("\n") + "\n"
    }

    // ---------- Import ----------

    /** Découpe tout le texte en lignes de champs, en respectant les guillemets (y compris retours à la ligne). */
    fun parseRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && text[i + 1] == '"') { cur.append('"'); i++ } else inQuotes = false
                } else cur.append(ch)
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { fields.add(cur.toString()); cur.setLength(0) }
                    '\r' -> { /* ignoré : \r\n traité comme \n */ }
                    '\n' -> { fields.add(cur.toString()); cur.setLength(0); rows.add(fields); fields = ArrayList() }
                    else -> cur.append(ch)
                }
            }
            i++
        }
        if (cur.isNotEmpty() || fields.isNotEmpty()) { fields.add(cur.toString()); rows.add(fields) }
        return rows
    }

    /** Retourne null si le texte ne contient pas de sauvegarde moussa EI. */
    fun import(raw: String): Import? {
        val start = raw.indexOf("## CHANTIERS")
        if (start < 0) return null
        val rows = parseRows(raw.substring(start))

        val chantiers = ArrayList<Chantier>()
        val fournisseurs = ArrayList<Fournisseur>()
        val archives = ArrayList<Archive>()
        val palettes = ArrayList<Palette>()
        val heures = LinkedHashMap<String, Heure>()
        val materiels = ArrayList<Materiel>()
        var contientMateriels = false
        val trajet = ArrayList<ArretTrajet>()
        var depart = ""
        val aRendre = ArrayList<PaletteARendre>()
        val reg = HashMap<String, String>()
        val produits = ArrayList<Produit>()

        var section = ""
        var headerDone = false
        for (r in rows) {
            val first = r.firstOrNull()?.trim().orEmpty()
            val isBlank = r.all { it.isBlank() }
            if (isBlank) { headerDone = false; continue }
            if (first.startsWith("## ")) {
                section = first.removePrefix("## ").trim().uppercase()
                if (section == "MATERIELS") contientMateriels = true
                headerDone = false
                continue
            }
            if (!headerDone) {
                headerDone = true
                // Ligne d'en-tête (id,nom,... ou date,debut,...) : on la saute.
                if (first == "id" || first == "date") continue
            }
            fun c(i: Int) = r.getOrNull(i)?.trim().orEmpty()
            // Les noms et notes gardent leurs espaces internes mais pas ceux des bords.
            when (section) {
                "CHANTIERS" -> if (r.size >= 6 && c(0).isNotEmpty()) chantiers += Chantier(
                    id = c(0), nom = c(1), surnom = c(2), adresse = c(3), chef = c(4), tel = c(5),
                    notes = c(6), statut = c(7).ifEmpty { STATUT_EN_COURS },
                    materiels = c(8).split(';').map { it.trim() }.filter { it.isNotEmpty() },
                )
                "FOURNISSEURS" -> if (r.size >= 2 && c(0).isNotEmpty()) fournisseurs += Fournisseur(c(0), c(1), c(2), c(3))
                "ARCHIVES" -> if (r.size >= 2 && c(0).isNotEmpty()) archives += Archive(c(0), c(1), c(2), c(3), c(4))
                "PALETTES" -> if (r.size >= 2 && c(0).isNotEmpty()) palettes += Palette(c(0), c(1), c(2), c(3), c(4))
                "HEURES" -> if (r.size >= 2 && DATE_KEY.matches(c(0))) heures[c(0)] = Heure(
                    debut = c(1).ifEmpty { Heure.DEBUT_PAR_DEFAUT }, fin = c(2), cp = c(3) == "1", note = c(4),
                )
                "MATERIELS" -> if (r.size >= 2 && c(0).isNotEmpty()) materiels += Materiel(
                    id = c(0), nom = c(1), fournisseurId = c(2), notes = c(3), recupere = c(4) == "1",
                    dateRecupere = c(5), emplacement = c(6),
                )
                "TRAJET" -> if (r.size >= 5 && c(0).isNotEmpty()) {
                    if (c(1) == "depart") depart = c(4)
                    else trajet += ArretTrajet(id = c(0), type = c(1), ref = c(2), nom = c(3), adresse = c(4))
                }
                "A_RENDRE" -> if (r.size >= 2 && c(0).isNotEmpty()) aRendre += PaletteARendre(
                    id = c(0), enseigne = c(1), type = c(2), quantite = c(3).toIntOrNull() ?: 1,
                    dateRecup = c(4), rendue = c(5) == "1", dateRendue = c(6), note = c(7),
                )
                "REGLAGES" -> if (r.size >= 2 && c(0).isNotEmpty()) reg[c(0)] = r.getOrNull(1).orEmpty()
                "PRODUITS" -> if (r.size >= 2 && c(0).isNotEmpty()) produits += Produit(
                    id = c(0), nom = c(1), fournisseurId = c(2), photo = c(3), note = c(4),
                    alias = c(5).split('|').map { it.trim() }.filter { it.isNotEmpty() },
                )
            }
        }
        return Import(
            AppData(
                chantiers, fournisseurs, materiels, archives, palettes, heures, trajet, depart, aRendre,
                Reglages(
                    entreprise = reg["entreprise"].orEmpty().trim(),
                    depot = reg["depot"].orEmpty().trim(),
                    finirAuDepot = reg["finirAuDepot"] != "0",
                    version = reg["version"]?.trim()?.toIntOrNull() ?: 0,
                    produitsMasques = reg["produitsMasques"].orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                ),
                produits,
            ),
            contientMateriels,
            contientReglages = reg.isNotEmpty(),
        )
    }

    private val DATE_KEY = Regex("""\d{4}-\d{2}-\d{2}""")
}
