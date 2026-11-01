package com.moussaei.gestion.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Toutes les règles de gestion, sous forme de fonctions pures AppData -> AppData.
 * Aucune dépendance Android : testé tel quel sur ordinateur.
 */
object Gestion {

    private val FR_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun dateFr(d: LocalDate): String = d.format(FR_DATE)

    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** Même forme que la v3.21 : "_" + 9 caractères. */
    fun newId(random: Random = Random.Default): String =
        "_" + (1..9).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    // ---------- Affichage / tri ----------

    fun nomAffiche(c: Chantier): String =
        c.nom.trim() + if (c.surnom.isNotBlank()) " — " + c.surnom.trim() else ""

    /** Matériels du chantier qui existent encore dans le camion. */
    fun materielsValides(d: AppData, c: Chantier): List<Materiel> {
        val byId = d.materiels.associateBy { it.id }
        return c.materiels.mapNotNull { byId[it] }
    }

    fun statut(d: AppData, c: Chantier): StatutEffectif = when {
        c.statut == STATUT_TERMINE -> StatutEffectif.TERMINE
        materielsValides(d, c).isNotEmpty() -> StatutEffectif.EN_LIVRAISON
        else -> StatutEffectif.EN_COURS
    }

    private val COLLATOR: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.FRENCH).apply {
        strength = java.text.Collator.PRIMARY
    }

    fun compareNoms(a: String, b: String): Int = COLLATOR.compare(a.trim(), b.trim())

    /** En livraison d'abord, puis en cours, terminés à la fin ; puis par nom. */
    fun chantiersTries(d: AppData): List<Chantier> = d.chantiers.sortedWith { a, b ->
        val sa = statut(d, a).ordinal
        val sb = statut(d, b).ordinal
        if (sa != sb) sa - sb else compareNoms(a.nom, b.nom)
    }

    fun fournisseursTries(d: AppData): List<Fournisseur> = d.fournisseurs.sortedWith { a, b -> compareNoms(a.nom, b.nom) }

    /** À charger d'abord, puis chargés (ordre d'ajout conservé sinon). */
    fun camionTrie(d: AppData): List<Materiel> = d.materiels.sortedBy { if (it.recupere) 1 else 0 }

    fun chantiersDuMateriel(d: AppData, materielId: String): List<Chantier> =
        d.chantiers.filter { materielId in it.materiels }

    fun fournisseur(d: AppData, id: String): Fournisseur? =
        if (id.isBlank()) null else d.fournisseurs.firstOrNull { it.id == id }

    fun nbACharger(d: AppData): Int = d.materiels.count { !it.recupere }

    /** Recherche simple insensible à la casse et aux accents. */
    fun correspond(query: String, vararg champs: String?): Boolean {
        val q = normaliser(query)
        if (q.isEmpty()) return true
        return champs.any { it != null && normaliser(it).contains(q) }
    }

    fun normaliser(s: String): String =
        java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    // ---------- Chantiers ----------

    fun ajouterChantier(d: AppData, c: Chantier): AppData =
        d.copy(chantiers = d.chantiers + c.copy(id = c.id.ifBlank { newId() }, materiels = emptyList(), statut = STATUT_EN_COURS))

    fun modifierChantier(d: AppData, id: String, nom: String, surnom: String, adresse: String, chef: String, tel: String, notes: String): AppData =
        d.copy(chantiers = d.chantiers.map {
            if (it.id == id) it.copy(nom = nom, surnom = surnom, adresse = adresse, chef = chef, tel = tel, notes = notes) else it
        })

    fun basculerStatut(d: AppData, id: String): AppData =
        d.copy(chantiers = d.chantiers.map {
            if (it.id == id) it.copy(statut = if (it.statut == STATUT_TERMINE) STATUT_EN_COURS else STATUT_TERMINE) else it
        })

    fun supprimerChantier(d: AppData, id: String): AppData = d.copy(chantiers = d.chantiers.filterNot { it.id == id })

    // ---------- Fournisseurs ----------

    fun ajouterFournisseur(d: AppData, nom: String, adresse: String, enseigne: String = ""): AppData =
        d.copy(fournisseurs = d.fournisseurs + Fournisseur(newId(), nom, adresse, enseigne.trim()))

    fun modifierFournisseur(d: AppData, id: String, nom: String, adresse: String, enseigne: String? = null): AppData =
        d.copy(fournisseurs = d.fournisseurs.map {
            if (it.id == id) it.copy(nom = nom, adresse = adresse, enseigne = enseigne?.trim() ?: it.enseigne) else it
        })

    fun supprimerFournisseur(d: AppData, id: String): AppData = d.copy(fournisseurs = d.fournisseurs.filterNot { it.id == id })

    // ---------- Camion (matériels) ----------

    private fun lier(d: AppData, chantierId: String, materielIds: List<String>): AppData =
        if (chantierId.isBlank() || materielIds.isEmpty()) d
        else d.copy(chantiers = d.chantiers.map { c ->
            if (c.id == chantierId) c.copy(materiels = (c.materiels + materielIds).distinct()) else c
        })

    /** Ajoute un matériel au camion, éventuellement lié à un chantier. Retourne les données et l'id créé. */
    fun ajouterMateriel(d: AppData, nom: String, fournisseurId: String, notes: String, chantierId: String): Pair<AppData, String> {
        val m = Materiel(id = newId(), nom = nom.trim(), fournisseurId = fournisseurId, notes = notes.trim())
        return lier(d.copy(materiels = d.materiels + m), chantierId, listOf(m.id)) to m.id
    }

    /** Liste rapide : une ligne = un matériel. Retourne les données et le nombre ajouté. */
    fun ajouterListe(d: AppData, texte: String, fournisseurId: String, chantierId: String): Pair<AppData, Int> {
        val lignes = texte.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val nouveaux = lignes.map { Materiel(id = newId(), nom = it, fournisseurId = fournisseurId) }
        val d2 = lier(d.copy(materiels = d.materiels + nouveaux), chantierId, nouveaux.map { it.id })
        return d2 to nouveaux.size
    }

    enum class AjoutResultat { AJOUTE, DEJA_PRESENT, INVALIDE }

    /** Depuis la fiche chantier : matériel existant, ou nouveau nom (réutilise un matériel du même nom). */
    fun ajouterAuChantier(d: AppData, chantierId: String, materielId: String, nouveauNom: String): Pair<AppData, AjoutResultat> {
        val chantier = d.chantiers.firstOrNull { it.id == chantierId } ?: return d to AjoutResultat.INVALIDE
        var data = d
        val idFinal: String = when {
            materielId.isNotBlank() -> materielId
            nouveauNom.isNotBlank() -> {
                val existant = d.materiels.firstOrNull { it.nom.trim().equals(nouveauNom.trim(), ignoreCase = true) }
                if (existant != null) existant.id else {
                    val (d2, id) = ajouterMateriel(d, nouveauNom, "", "", "")
                    data = d2
                    id
                }
            }
            else -> return d to AjoutResultat.INVALIDE
        }
        if (idFinal in chantier.materiels) return data to AjoutResultat.DEJA_PRESENT
        return lier(data, chantierId, listOf(idFinal)) to AjoutResultat.AJOUTE
    }

    fun modifierMateriel(d: AppData, id: String, nom: String, fournisseurId: String, notes: String): AppData =
        d.copy(materiels = d.materiels.map { if (it.id == id) it.copy(nom = nom, fournisseurId = fournisseurId, notes = notes) else it })

    fun basculerCharge(d: AppData, id: String, aujourdhui: LocalDate): AppData =
        d.copy(materiels = d.materiels.map {
            if (it.id != id) it
            else if (it.recupere) it.copy(recupere = false, dateRecupere = "")
            else it.copy(recupere = true, dateRecupere = dateFr(aujourdhui))
        })

    /** Cabine / plateau ; re-toucher le même emplacement l'enlève. */
    fun basculerEmplacement(d: AppData, id: String, emplacement: String): AppData =
        d.copy(materiels = d.materiels.map {
            if (it.id == id) it.copy(emplacement = if (it.emplacement == emplacement) "" else emplacement) else it
        })

    fun basculerAffectation(d: AppData, materielId: String, chantierId: String): AppData =
        d.copy(chantiers = d.chantiers.map { c ->
            if (c.id != chantierId) c
            else if (materielId in c.materiels) c.copy(materiels = c.materiels - materielId)
            else c.copy(materiels = c.materiels + materielId)
        })

    fun retirerDuChantier(d: AppData, chantierId: String, materielId: String): AppData =
        d.copy(chantiers = d.chantiers.map { c -> if (c.id == chantierId) c.copy(materiels = c.materiels - materielId) else c })

    /** Supprime le matériel et ses liens vers les chantiers. */
    fun supprimerMateriel(d: AppData, id: String): AppData =
        d.copy(
            materiels = d.materiels.filterNot { it.id == id },
            chantiers = d.chantiers.map { c -> if (id in c.materiels) c.copy(materiels = c.materiels - id) else c },
        )

    /**
     * Livré : crée une archive puis retire le matériel du camion.
     * [depuisChantierId] renseigné = livraison depuis la fiche d'un chantier (seul ce chantier est noté).
     */
    fun livrer(d: AppData, materielId: String, aujourdhui: LocalDate, depuisChantierId: String = ""): AppData {
        val m = d.materiels.firstOrNull { it.id == materielId } ?: return d
        val fournisseurNom = fournisseur(d, m.fournisseurId)?.nom?.trim() ?: "Inconnu"
        val chantiersNom = if (depuisChantierId.isNotBlank()) {
            d.chantiers.firstOrNull { it.id == depuisChantierId }?.let { nomAffiche(it) } ?: "Inconnu"
        } else {
            chantiersDuMateriel(d, materielId).joinToString(", ") { nomAffiche(it) }.ifEmpty { "Aucun" }
        }
        val archive = Archive(newId(), m.nom, fournisseurNom, chantiersNom, dateFr(aujourdhui))
        return supprimerMateriel(d.copy(archives = listOf(archive) + d.archives), materielId)
    }

    fun supprimerArchive(d: AppData, id: String): AppData = d.copy(archives = d.archives.filterNot { it.id == id })

    // ---------- Palettes ----------

    fun enregistrerPalette(d: AppData, id: String, nom: String, enseigne: String, note: String): AppData {
        val agence = agencesDe(d, enseigne).firstOrNull()?.id ?: ""
        return if (id.isNotBlank() && d.palettes.any { it.id == id })
            d.copy(palettes = d.palettes.map { if (it.id == id) it.copy(nom = nom, enseigne = enseigne, fournisseurId = agence, note = note) else it })
        else d.copy(palettes = d.palettes + Palette(newId(), nom, agence, note, enseigne))
    }

    fun supprimerPalette(d: AppData, id: String): AppData = d.copy(palettes = d.palettes.filterNot { it.id == id })

    // ---------- Heures ----------

    fun enregistrerHeure(d: AppData, date: LocalDate, h: Heure): AppData = d.copy(heures = d.heures + (date.toString() to h))

    fun supprimerHeure(d: AppData, date: LocalDate): AppData = d.copy(heures = d.heures - date.toString())

    // ---------- Import ----------

    /**
     * Comme la v3.21 : chantiers, fournisseurs et archives sont remplacés ; palettes et heures
     * seulement si le fichier en contient. Le camion est remplacé si le fichier a une section
     * MATERIELS, sinon on garde le camion actuel. Les liens chantier → matériel orphelins sont retirés.
     */
    fun importer(actuel: AppData, imp: Csv.Import): AppData {
        val n = imp.data
        val materiels = if (imp.contientMateriels) n.materiels else actuel.materiels
        val ids = materiels.map { it.id }.toSet()
        return AppData(
            chantiers = n.chantiers.map { c -> c.copy(materiels = c.materiels.filter { it in ids }) },
            fournisseurs = n.fournisseurs,
            materiels = materiels,
            archives = n.archives,
            palettes = if (n.palettes.isNotEmpty()) n.palettes else actuel.palettes,
            heures = if (n.heures.isNotEmpty()) n.heures else actuel.heures,
            trajet = if (n.trajet.isNotEmpty()) n.trajet else actuel.trajet,
            trajetDepart = if (n.trajet.isNotEmpty() || n.trajetDepart.isNotBlank()) n.trajetDepart else actuel.trajetDepart,
            aRendre = if (n.aRendre.isNotEmpty()) n.aRendre else actuel.aRendre,
            // Fichier ancien (sans réglages) : on garde les réglages actuels mais on refait la migration.
            reglages = if (imp.contientReglages) n.reglages else actuel.reglages.copy(version = 0),
        )
    }

    // ---------- Enseignes ----------

    /** Enseigne d'un fournisseur ; sans enseigne, son propre nom. */
    fun enseigneDe(f: Fournisseur): String = f.enseigne.trim().ifEmpty { f.nom.trim() }

    fun memeEnseigne(a: String, b: String): Boolean = normaliser(a) == normaliser(b)

    fun agencesDe(d: AppData, enseigne: String): List<Fournisseur> =
        d.fournisseurs.filter { memeEnseigne(enseigneDe(it), enseigne) }.sortedWith { a, b -> compareNoms(a.nom, b.nom) }

    /** Enseignes connues (une par groupe de fournisseurs), triées. */
    fun enseignes(d: AppData): List<String> =
        d.fournisseurs.groupBy { normaliser(enseigneDe(it)) }.values
            .map { g -> enseigneDe(g.first()) }
            .sortedWith { a, b -> compareNoms(a, b) }

    /** Enseigne d'une palette (ancien format : déduite de son agence d'origine). */
    fun enseigneDePalette(d: AppData, p: Palette): String =
        p.enseigne.trim().ifEmpty { fournisseur(d, p.fournisseurId)?.let { enseigneDe(it) } ?: "" }

    private val ENSEIGNES_CONNUES = listOf(
        Regex("^points? ?p\\b|^\\(?p\\.p\\)?") to "Point P",
        Regex("^loxam") to "Loxam",
        Regex("^kiloutou") to "Kiloutou",
        Regex("^raboni") to "Raboni",
        Regex("^acipar") to "Acipar",
        Regex("^prolians") to "Prolians",
        Regex("derichebourg") to "Derichebourg",
        Regex("^bpm\\b") to "BPM",
    )

    /** Devine l'enseigne d'après le nom (« Points P (P.P) Aubervilliers » → « Point P »), "" si inconnue. */
    fun devinerEnseigne(nom: String): String {
        val n = normaliser(nom)
        return ENSEIGNES_CONNUES.firstOrNull { it.first.containsMatchIn(n) }?.second ?: ""
    }

    /** Remplit les enseignes manquantes (fournisseurs devinés, palettes d'après leur agence). */
    fun completerEnseignes(d: AppData): AppData {
        val f2 = d.fournisseurs.map { if (it.enseigne.isBlank()) it.copy(enseigne = devinerEnseigne(it.nom)) else it }
        val d2 = d.copy(fournisseurs = f2)
        return d2.copy(palettes = d2.palettes.map { p -> if (p.enseigne.isBlank()) p.copy(enseigne = enseigneDePalette(d2, p)) else p })
    }

    // ---------- Agences (catalogue Île-de-France) ----------

    data class AgenceCatalogue(val enseigne: String, val nom: String, val adresse: String)

    /** Catalogue au format CSV : enseigne,nom,adresse[,source] */
    fun lireCatalogue(texte: String): List<AgenceCatalogue> =
        Csv.parseRows(texte).drop(1).mapNotNull { r ->
            val e = r.getOrNull(0)?.trim().orEmpty()
            val n = r.getOrNull(1)?.trim().orEmpty()
            val a = r.getOrNull(2)?.trim().orEmpty()
            if (e.isEmpty() || n.isEmpty() || a.isEmpty()) null else AgenceCatalogue(e, n, a)
        }

    private fun cleAdresse(a: String): String = normaliser(a).replace(Regex("[^a-z0-9]"), "")

    /** Code postal + numéro de rue, pour reconnaître une même agence écrite différemment. */
    private fun signature(a: String): String? {
        val n = normaliser(a)
        val cp = Regex("\\b(7[5789]|9[1-5])\\d{3}\\b").find(n)?.value ?: return null
        // Numéro suivi d'un type de voie, où qu'il soit (« Face au, 29 Quai … » → 29).
        val num = Regex("\\b(\\d+)\\s*(bis|ter)?\\s*,?\\s*(rue|quai|av|avenue|bd|boulevard|chemin|chem|route|rte|allee|place|impasse|voie|cours|square|zi|za|zac|parc)\\b")
            .find(n)?.groupValues?.get(1) ?: return null
        return "$cp-$num"
    }

    private fun idAgence(a: AgenceCatalogue): String =
        "_ag" + Integer.toHexString((normaliser(a.enseigne) + "|" + cleAdresse(a.adresse)).hashCode()).padStart(8, '0')

    /**
     * Ajoute les agences du catalogue des enseignes que l'utilisateur a déjà dans ses fournisseurs.
     * Ne modifie aucune fiche existante et ne crée pas de doublon (même id, même adresse
     * ou même code postal + numéro chez la même enseigne).
     */
    fun ajouterAgences(d: AppData, catalogue: List<AgenceCatalogue>): Pair<AppData, Int> {
        val siennes = d.fournisseurs.map { normaliser(enseigneDe(it)) }.toSet()
        val nouveaux = ArrayList<Fournisseur>()
        val tous = d.fournisseurs.toMutableList()
        catalogue.forEach { a ->
            if (normaliser(a.enseigne) !in siennes) return@forEach
            val id = idAgence(a)
            val sig = signature(a.adresse)
            val cle = cleAdresse(a.adresse)
            val doublon = tous.any { f ->
                f.id == id || (memeEnseigne(enseigneDe(f), a.enseigne) &&
                    (cleAdresse(f.adresse) == cle || (sig != null && signature(f.adresse) == sig) ||
                        // fiche sans code postal (« 41 Boulevard Anatole France ») : rue contenue dans l'adresse
                        (cleAdresse(f.adresse).let { k -> k.length >= 12 && k[0].isDigit() && cle.contains(k) })))
            }
            if (!doublon) {
                val f = Fournisseur(id, a.nom, a.adresse, a.enseigne)
                nouveaux += f
                tous += f
            }
        }
        return d.copy(fournisseurs = d.fournisseurs + nouveaux) to nouveaux.size
    }

    /** Migration vers la v4.2 (une seule fois ; refaite après l'import d'un ancien fichier). */
    fun migrer(d: AppData, catalogue: List<AgenceCatalogue>): AppData {
        if (d.reglages.version >= VERSION_DONNEES) return d
        var x = completerEnseignes(d)
        x = ajouterAgences(x, catalogue).first
        if (x.reglages.depot.isBlank()) {
            // Le fournisseur « Dépôt » existant devient le dépôt de départ / retour.
            val depot = d.fournisseurs.firstOrNull { normaliser(it.nom) == "depot" && it.adresse.isNotBlank() }
            if (depot != null) x = x.copy(reglages = x.reglages.copy(depot = depot.adresse.trim()))
        }
        return x.copy(reglages = x.reglages.copy(version = VERSION_DONNEES))
    }

    fun modifierReglages(d: AppData, r: Reglages): AppData = d.copy(reglages = r.copy(version = d.reglages.version))

    // ---------- Palettes à rendre ----------

    fun recupererPalettes(d: AppData, enseigne: String, type: String, quantite: Int, note: String, aujourdhui: LocalDate): AppData =
        d.copy(aRendre = listOf(PaletteARendre(newId(), enseigne.trim(), type.trim(), quantite.coerceAtLeast(1), dateFr(aujourdhui), note = note.trim())) + d.aRendre)

    fun marquerRendue(d: AppData, id: String, aujourdhui: LocalDate): AppData =
        d.copy(aRendre = d.aRendre.map { if (it.id == id) it.copy(rendue = true, dateRendue = dateFr(aujourdhui)) else it })

    fun annulerRendue(d: AppData, id: String): AppData =
        d.copy(aRendre = d.aRendre.map { if (it.id == id) it.copy(rendue = false, dateRendue = "") else it })

    fun changerQuantite(d: AppData, id: String, delta: Int): AppData =
        d.copy(aRendre = d.aRendre.map { if (it.id == id) it.copy(quantite = (it.quantite + delta).coerceAtLeast(1)) else it })

    fun supprimerARendre(d: AppData, id: String): AppData = d.copy(aRendre = d.aRendre.filterNot { it.id == id })

    fun nbARendre(d: AppData): Int = d.aRendre.filterNot { it.rendue }.sumOf { it.quantite }

    /** Palettes encore à rendre, par enseigne (total), enseignes triées. */
    fun aRendreParEnseigne(d: AppData): List<Pair<String, List<PaletteARendre>>> =
        d.aRendre.filterNot { it.rendue }.groupBy { normaliser(it.enseigne) }.values
            .map { g -> g.first().enseigne to g }
            .sortedWith { a, b -> compareNoms(a.first, b.first) }

    // ---------- Trajet ----------

    data class Arret(val nom: String, val adresse: String, val estChantier: Boolean)

    /** Arrêt mis à jour avec la fiche actuelle (nom / adresse modifiés depuis l'ajout). */
    fun arretAJour(d: AppData, a: ArretTrajet): ArretTrajet = when (a.type) {
        ARRET_CHANTIER -> d.chantiers.firstOrNull { it.id == a.ref }?.let { a.copy(nom = nomAffiche(it), adresse = it.adresse) } ?: a
        ARRET_FOURNISSEUR -> d.fournisseurs.firstOrNull { it.id == a.ref }?.let { a.copy(nom = it.nom.trim(), adresse = it.adresse) } ?: a
        else -> a
    }

    fun trajetAJour(d: AppData): List<ArretTrajet> = d.trajet.map { arretAJour(d, it) }

    fun dansTrajet(d: AppData, type: String, ref: String): Boolean = d.trajet.any { it.type == type && it.ref == ref }

    enum class AjoutTrajet { AJOUTE, DEJA, SANS_ADRESSE }

    fun ajouterAuTrajet(d: AppData, type: String, ref: String, nom: String, adresse: String): Pair<AppData, AjoutTrajet> {
        if (adresse.isBlank()) return d to AjoutTrajet.SANS_ADRESSE
        if (ref.isNotBlank() && dansTrajet(d, type, ref)) return d to AjoutTrajet.DEJA
        val a = ArretTrajet(newId(), type, ref, nom.trim().ifEmpty { adresse.trim() }, adresse.trim())
        return d.copy(trajet = d.trajet + a) to AjoutTrajet.AJOUTE
    }

    fun ajouterChantierAuTrajet(d: AppData, c: Chantier) = ajouterAuTrajet(d, ARRET_CHANTIER, c.id, nomAffiche(c), c.adresse)

    fun ajouterFournisseurAuTrajet(d: AppData, f: Fournisseur) = ajouterAuTrajet(d, ARRET_FOURNISSEUR, f.id, f.nom, f.adresse)

    /** Ajoute ou retire (si déjà présent) un chantier / fournisseur. */
    fun basculerDansTrajet(d: AppData, type: String, ref: String, nom: String, adresse: String): AppData =
        if (dansTrajet(d, type, ref)) d.copy(trajet = d.trajet.filterNot { it.type == type && it.ref == ref })
        else ajouterAuTrajet(d, type, ref, nom, adresse).first

    fun retirerDuTrajet(d: AppData, id: String): AppData = d.copy(trajet = d.trajet.filterNot { it.id == id })

    /** Déplace un arrêt d'un cran (-1 = monter, +1 = descendre). */
    fun deplacerArret(d: AppData, id: String, sens: Int): AppData {
        val l = d.trajet.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + sens
        if (i < 0 || j < 0 || j >= l.size) return d
        val tmp = l[i]; l[i] = l[j]; l[j] = tmp
        return d.copy(trajet = l)
    }

    fun viderTrajet(d: AppData): AppData = d.copy(trajet = emptyList())

    fun changerDepart(d: AppData, adresse: String): AppData = d.copy(trajetDepart = adresse.trim())

    /** Estimation de la v3.21 : ~45 min vers un chantier, ~30 min vers un fournisseur ou une adresse. */
    /** Adresse d'arrivée du trajet (dépôt) ou "" si le retour au dépôt est désactivé. */
    fun arrivee(d: AppData): String = if (d.reglages.finirAuDepot) d.reglages.depot else ""

    fun minutesEstimees(arrets: List<ArretTrajet>, depuisDepart: Boolean = true): Int =
        (if (depuisDepart) arrets else arrets.drop(1)).sumOf { if (it.type == ARRET_CHANTIER) 45 else 30 }

    fun dureeTexte(min: Int): String {
        val h = min / 60
        val m = min % 60
        return if (h > 0) h.toString() + "h" + (if (m > 0) m.toString().padStart(2, '0') else "") else "$m min"
    }

    /** Google Maps accepte au plus 9 étapes intermédiaires + la destination dans un lien. */
    const val ARRETS_MAX_PAR_LIEN = 10

    data class PartieTrajet(val numero: Int, val premier: Int, val dernier: Int, val url: String)

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /**
     * Liens Google Maps du trajet. Départ vide = ma position actuelle (Maps la prend
     * automatiquement quand il n'y a pas d'origine). Au-delà de 10 arrêts, le trajet est découpé :
     * chaque partie repart du dernier arrêt de la précédente.
     */
    fun liensGoogleMaps(arrets0: List<ArretTrajet>, depart: String, arrivee: String = ""): List<PartieTrajet> {
        if (arrets0.isEmpty()) return emptyList()
        val arrets = if (arrivee.isNotBlank() && cleAdresse(arrets0.last().adresse) != cleAdresse(arrivee))
            arrets0 + ArretTrajet("_depot", ARRET_DEPOT, "", "Dépôt", arrivee.trim())
        else arrets0
        val parties = ArrayList<PartieTrajet>()
        var origine: String? = depart.trim().ifEmpty { null }
        var debut = 0
        var numero = 1
        while (debut < arrets.size) {
            val fin = minOf(arrets.size, debut + ARRETS_MAX_PAR_LIEN)
            val morceau = arrets.subList(debut, fin)
            val sb = StringBuilder("https://www.google.com/maps/dir/?api=1")
            if (origine != null) sb.append("&origin=").append(enc(origine))
            sb.append("&destination=").append(enc(morceau.last().adresse))
            val milieu = morceau.dropLast(1)
            if (milieu.isNotEmpty()) sb.append("&waypoints=").append(milieu.joinToString("%7C") { enc(it.adresse) })
            sb.append("&travelmode=driving")
            parties += PartieTrajet(numero, debut + 1, fin, sb.toString())
            origine = morceau.last().adresse
            debut = fin
            numero++
        }
        return parties
    }

    // ---------- Recherche globale (accueil) ----------

    enum class TypeResultat { CHANTIER, FOURNISSEUR, PALETTE }

    data class Resultat(
        val type: TypeResultat,
        val id: String,
        val titre: String,
        val detail: String,
        val adresse: String,
        /** Pour ➕ Trajet : chantier ou fournisseur visé (le fournisseur pour une palette). */
        val typeArret: String,
        val refArret: String,
        val nomArret: String,
        /** Pour une palette : son enseigne (afficher toutes les agences). */
        val enseigne: String = "",
    )

    fun rechercher(d: AppData, q: String): List<Resultat> {
        if (normaliser(q).isEmpty()) return emptyList()
        val r = ArrayList<Resultat>()
        chantiersTries(d).filter { correspond(q, it.nom, it.surnom, it.adresse, it.chef, it.notes) }.forEach { c ->
            r += Resultat(TypeResultat.CHANTIER, c.id, nomAffiche(c), c.chef.trim(), c.adresse, ARRET_CHANTIER, c.id, nomAffiche(c))
        }
        fournisseursTries(d).filter { correspond(q, it.nom, it.adresse) }.forEach { f ->
            r += Resultat(TypeResultat.FOURNISSEUR, f.id, f.nom.trim(), "", f.adresse, ARRET_FOURNISSEUR, f.id, f.nom.trim())
        }
        d.palettes.filter { p -> correspond(q, p.nom, p.note, enseigneDePalette(d, p)) }
            .sortedWith { a, b -> compareNoms(a.nom, b.nom) }
            .forEach { p ->
                val e = enseigneDePalette(d, p)
                val agences = agencesDe(d, e)
                val unique = agences.singleOrNull()
                r += Resultat(
                    TypeResultat.PALETTE, p.id, p.nom,
                    listOfNotNull(e.ifBlank { null }, p.note.ifBlank { null }, if (agences.size > 1) "${agences.size} agences" else null).joinToString(" · "),
                    unique?.adresse ?: "", ARRET_FOURNISSEUR, unique?.id ?: "", unique?.nom?.trim() ?: "",
                    enseigne = e,
                )
            }
        return r
    }
}
