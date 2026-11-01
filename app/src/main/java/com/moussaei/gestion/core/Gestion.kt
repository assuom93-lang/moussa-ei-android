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

    fun ajouterFournisseur(d: AppData, nom: String, adresse: String): AppData =
        d.copy(fournisseurs = d.fournisseurs + Fournisseur(newId(), nom, adresse))

    fun modifierFournisseur(d: AppData, id: String, nom: String, adresse: String): AppData =
        d.copy(fournisseurs = d.fournisseurs.map { if (it.id == id) it.copy(nom = nom, adresse = adresse) else it })

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

    fun enregistrerPalette(d: AppData, id: String, nom: String, fournisseurId: String, note: String): AppData =
        if (id.isNotBlank() && d.palettes.any { it.id == id })
            d.copy(palettes = d.palettes.map { if (it.id == id) it.copy(nom = nom, fournisseurId = fournisseurId, note = note) else it })
        else d.copy(palettes = d.palettes + Palette(newId(), nom, fournisseurId, note))

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
        )
    }

    // ---------- Itinéraire ----------

    data class Arret(val nom: String, val adresse: String, val estChantier: Boolean)

    /** Estimation de la v3.21 : ~45 min vers un chantier, ~30 min vers un fournisseur. */
    fun minutesEstimees(arrets: List<Arret>): Int =
        arrets.drop(1).sumOf { if (it.estChantier) 45 else 30 }

    fun dureeTexte(min: Int): String {
        val h = min / 60
        val m = min % 60
        return if (h > 0) h.toString() + "h" + (if (m > 0) m.toString().padStart(2, '0') else "") else "$m min"
    }

    fun urlGoogleMaps(arrets: List<Arret>): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        val sb = StringBuilder("https://www.google.com/maps/dir/?api=1")
        sb.append("&origin=").append(enc(arrets.first().adresse))
        sb.append("&destination=").append(enc(arrets.last().adresse))
        val milieu = arrets.drop(1).dropLast(1)
        if (milieu.isNotEmpty()) sb.append("&waypoints=").append(milieu.joinToString("%7C") { enc(it.adresse) })
        sb.append("&travelmode=driving")
        return sb.toString()
    }
}
