package com.moussaei.gestion.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** Un itinéraire enregistré, rattaché à un jour travaillé. */
data class TrajetEnregistre(
    val id: String,
    /** Jour travaillé aaaa-mm-jj. */
    val date: String,
    /** Heure d'enregistrement / de lancement HH:mm. */
    val heure: String = "",
    val nom: String = "",
    /** Départ ("" = ma position / dépôt). */
    val depart: String = "",
    val arrets: List<ArretTrajet> = emptyList(),
    /** Arrivée (dépôt) ou "". */
    val arrivee: String = "",
    /** Calcul poids lourd (0 = pas encore calculé). */
    val distanceM: Int = 0,
    val dureeS: Int = 0,
    /** Tracé (polyline encodé) pour la carte. */
    val geometrie: String = "",
    /** Durée de chaque tronçon, en secondes. */
    val troncons: List<Int> = emptyList(),
) {
    val calcule: Boolean get() = dureeS > 0
}

data class Coord(val lat: Double, val lon: Double)

object Trajets {

    // ---------- Enregistrement ----------

    private fun signature(arrets: List<ArretTrajet>, depart: String): String =
        depart.trim().lowercase() + "|" + arrets.joinToString("|") { it.type + ":" + it.ref + ":" + Gestion.normaliser(it.adresse) }

    fun nomParDefaut(d: AppData, date: LocalDate): String {
        val n = d.trajetsEnregistres.count { it.date == date.toString() } + 1
        return "Tournée du " + Heures.titreJour(date).lowercase() + if (n > 1) " ($n)" else ""
    }

    /**
     * Enregistre le trajet en cours sur [date]. Si le même trajet (mêmes arrêts, même départ)
     * est déjà enregistré ce jour-là, il est mis à jour au lieu d'être dupliqué.
     * Retourne les données et l'id du trajet enregistré ("" si le trajet est vide).
     */
    fun enregistrer(d: AppData, date: LocalDate, heure: String, nom: String = ""): Pair<AppData, String> {
        val arrets = Gestion.trajetAJour(d)
        if (arrets.isEmpty()) return d to ""
        val sig = signature(arrets, d.trajetDepart)
        val existant = d.trajetsEnregistres.firstOrNull { it.date == date.toString() && signature(it.arrets, it.depart) == sig }
        if (existant != null) {
            val maj = existant.copy(
                nom = nom.ifBlank { existant.nom },
                arrivee = Gestion.arrivee(d),
            )
            return d.copy(trajetsEnregistres = d.trajetsEnregistres.map { if (it.id == existant.id) maj else it }) to existant.id
        }
        val t = TrajetEnregistre(
            id = Gestion.newId(),
            date = date.toString(),
            heure = heure,
            nom = nom.ifBlank { nomParDefaut(d, date) },
            depart = d.trajetDepart,
            arrets = arrets.map { it.copy(id = Gestion.newId()) },
            arrivee = Gestion.arrivee(d),
        )
        return d.copy(trajetsEnregistres = listOf(t) + d.trajetsEnregistres) to t.id
    }

    fun parId(d: AppData, id: String): TrajetEnregistre? = d.trajetsEnregistres.firstOrNull { it.id == id }

    private fun remplacer(d: AppData, id: String, f: (TrajetEnregistre) -> TrajetEnregistre): AppData =
        d.copy(trajetsEnregistres = d.trajetsEnregistres.map { if (it.id == id) f(it) else it })

    fun renommer(d: AppData, id: String, nom: String): AppData = remplacer(d, id) { it.copy(nom = nom.trim().ifEmpty { it.nom }) }

    fun changerJour(d: AppData, id: String, date: LocalDate): AppData = remplacer(d, id) { it.copy(date = date.toString()) }

    /** Modifier les arrêts efface le calcul (il faut le refaire). */
    fun changerArrets(d: AppData, id: String, arrets: List<ArretTrajet>): AppData =
        remplacer(d, id) { it.copy(arrets = arrets, distanceM = 0, dureeS = 0, geometrie = "", troncons = emptyList()) }

    fun retirerArret(d: AppData, id: String, arretId: String): AppData =
        parId(d, id)?.let { t -> changerArrets(d, id, t.arrets.filterNot { it.id == arretId }) } ?: d

    fun deplacerArret(d: AppData, id: String, arretId: String, sens: Int): AppData {
        val t = parId(d, id) ?: return d
        val l = t.arrets.toMutableList()
        val i = l.indexOfFirst { it.id == arretId }
        val j = i + sens
        if (i < 0 || j < 0 || j >= l.size) return d
        val x = l[i]; l[i] = l[j]; l[j] = x
        return changerArrets(d, id, l)
    }

    fun ajouterArret(d: AppData, id: String, type: String, ref: String, nom: String, adresse: String): AppData {
        val t = parId(d, id) ?: return d
        if (adresse.isBlank()) return d
        return changerArrets(d, id, t.arrets + ArretTrajet(Gestion.newId(), type, ref, nom.trim().ifEmpty { adresse.trim() }, adresse.trim()))
    }

    fun changerDepartArrivee(d: AppData, id: String, depart: String, arrivee: String): AppData =
        remplacer(d, id) { it.copy(depart = depart.trim(), arrivee = arrivee.trim(), distanceM = 0, dureeS = 0, geometrie = "", troncons = emptyList()) }

    fun supprimer(d: AppData, id: String): AppData = d.copy(trajetsEnregistres = d.trajetsEnregistres.filterNot { it.id == id })

    /** Recharge un trajet enregistré comme trajet en cours (pour le refaire). */
    fun refaire(d: AppData, id: String): AppData {
        val t = parId(d, id) ?: return d
        return d.copy(trajet = t.arrets.map { it.copy(id = Gestion.newId()) }, trajetDepart = t.depart)
    }

    fun enregistrerCalcul(d: AppData, id: String, distanceM: Int, dureeS: Int, geometrie: String, troncons: List<Int>): AppData =
        remplacer(d, id) { it.copy(distanceM = distanceM, dureeS = dureeS, geometrie = geometrie, troncons = troncons) }

    fun duJour(d: AppData, date: LocalDate): List<TrajetEnregistre> =
        d.trajetsEnregistres.filter { it.date == date.toString() }.sortedBy { it.heure }

    /** Historique, plus récent en premier, filtré par la recherche (nom, arrêts, date). */
    fun historique(d: AppData, q: String): List<TrajetEnregistre> =
        d.trajetsEnregistres
            .filter { t -> Gestion.correspond(q, t.nom, t.date, t.arrets.joinToString(" ") { it.nom + " " + it.adresse }) }
            .sortedWith(compareByDescending<TrajetEnregistre> { it.date }.thenByDescending { it.heure })

    // ---------- Départ / arrivée réels pour le calcul ----------

    /** Départ du calcul : départ choisi, sinon le dépôt (prise de poste), sinon le 1er arrêt. */
    fun departCalcul(d: AppData, t: TrajetEnregistre): String =
        t.depart.ifBlank { d.reglages.depot }.ifBlank { t.arrets.firstOrNull()?.adresse ?: "" }

    /** Adresses du calcul, dans l'ordre : départ, arrêts, arrivée (sans doublon consécutif). */
    fun adressesCalcul(d: AppData, t: TrajetEnregistre): List<String> {
        val l = ArrayList<String>()
        fun add(a: String) { if (a.isNotBlank() && (l.isEmpty() || Gestion.normaliser(l.last()) != Gestion.normaliser(a))) l += a.trim() }
        add(departCalcul(d, t))
        t.arrets.forEach { add(it.adresse) }
        add(t.arrivee)
        return l
    }

    // ---------- Partage ----------

    fun texteAPartager(t: TrajetEnregistre): String {
        val sb = StringBuilder()
        val date = try { Heures.titreJour(LocalDate.parse(t.date)) } catch (e: Exception) { t.date }
        sb.append("🗺️ ").append(t.nom).append("\n").append(date).append(if (t.heure.isNotBlank()) " · " + t.heure else "").append("\n\n")
        if (t.depart.isNotBlank()) sb.append("Départ : ").append(t.depart).append("\n")
        t.arrets.forEachIndexed { i, a ->
            sb.append(i + 1).append(". ").append(a.nom)
            if (a.adresse != a.nom) sb.append(" — ").append(a.adresse)
            sb.append("\n")
        }
        if (t.arrivee.isNotBlank()) sb.append("Arrivée : ").append(t.arrivee).append("\n")
        if (t.calcule) sb.append("\n🚛 ").append(km(t.distanceM)).append(" · ").append(Heures.formatMinutes(t.dureeS / 60)).append(" de route (poids lourd, sans bouchons)\n")
        val liens = Gestion.liensGoogleMaps(t.arrets, t.depart, t.arrivee)
        if (liens.isNotEmpty()) {
            sb.append("\nGoogle Maps :\n")
            liens.forEach { p -> sb.append(if (liens.size > 1) "Partie ${p.numero} : " else "").append(p.url).append("\n") }
        }
        return sb.toString().trimEnd()
    }

    fun km(m: Int): String = if (m >= 10_000) (m / 1000.0).roundToInt().toString() + " km" else String.format(java.util.Locale.FRANCE, "%.1f km", m / 1000.0)

    // ---------- Coordonnées ----------

    private val DMS = Regex("""(\d{1,3})°\s*(\d{1,2})['′]\s*([\d.,]+)?["″]?\s*([NSns])\s*[, ]*\s*(\d{1,3})°\s*(\d{1,2})['′]\s*([\d.,]+)?["″]?\s*([EOWeow])""")
    private val DEC = Regex("""^\s*(-?\d{1,2}[.,]\d+)\s*[, ;]\s*(-?\d{1,3}[.,]\d+)\s*$""")

    /** Adresse donnée en coordonnées GPS (48°51'33.3"N 2°20'16.7"E ou 48.8592, 2.3379) → coordonnées, sinon null. */
    fun coordonneesDirectes(adresse: String): Coord? {
        DMS.find(adresse)?.let { m ->
            fun v(dg: String, mn: String, s: String?) = dg.toDouble() + mn.toDouble() / 60 + (s?.replace(',', '.')?.toDoubleOrNull() ?: 0.0) / 3600
            var lat = v(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null })
            var lon = v(m.groupValues[5], m.groupValues[6], m.groupValues[7].ifEmpty { null })
            if (m.groupValues[4].uppercase() == "S") lat = -lat
            if (m.groupValues[8].uppercase() in setOf("W", "O")) lon = -lon
            return Coord(lat, lon)
        }
        DEC.find(adresse)?.let { m ->
            return Coord(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2].replace(',', '.').toDouble())
        }
        return null
    }

    fun cleGeocode(adresse: String): String = Gestion.normaliser(adresse).replace(Regex("\\s+"), " ")

    fun memoriserGeocode(d: AppData, adresse: String, c: Coord): AppData =
        d.copy(geocodes = d.geocodes + (cleGeocode(adresse) to c))

    fun coordonnees(d: AppData, adresse: String): Coord? = coordonneesDirectes(adresse) ?: d.geocodes[cleGeocode(adresse)]

    /** Décode un tracé « polyline » (format Google, précision 5) en points. */
    fun decoderPolyline(s: String, precision: Int = 5): List<Coord> {
        val res = ArrayList<Coord>()
        val f = Math.pow(10.0, precision.toDouble())
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < s.length) {
            for (k in 0..1) {
                var shift = 0
                var result = 0L
                var b: Int
                do {
                    b = s[i++].code - 63
                    result = result or ((b and 0x1f).toLong() shl shift)
                    shift += 5
                } while (b >= 0x20 && i < s.length)
                val delta = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
                if (k == 0) lat += delta else lon += delta
            }
            res += Coord(lat / f, lon / f)
        }
        return res
    }

    // ---------- Temps de conduite ----------

    /** Conduite estimée du jour (somme des trajets calculés), en minutes. */
    fun conduiteEstimee(d: AppData, date: LocalDate): Int = duJour(d, date).sumOf { it.dureeS } / 60

    /** Conduite retenue : le relevé saisi s'il existe, sinon l'estimation. */
    fun conduite(d: AppData, date: LocalDate): Int {
        val saisie = d.heures[date.toString()]?.conduiteMin ?: -1
        return if (saisie >= 0) saisie else conduiteEstimee(d, date)
    }

    fun lundi(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    fun conduiteSemaine(d: AppData, date: LocalDate): Int {
        val l = lundi(date)
        return (0 until 7).sumOf { conduite(d, l.plusDays(it.toLong())) }
    }

    const val PAUSE_APRES = 4 * 60 + 30
    const val MAX_JOUR = 9 * 60
    const val MAX_JOUR_ETENDU = 10 * 60
    const val MAX_SEMAINE = 56 * 60
    const val MAX_DEUX_SEMAINES = 90 * 60

    enum class Niveau { INFO, ATTENTION, DEPASSEMENT }

    data class Alerte(val niveau: Niveau, val texte: String)

    /** Rappels de la réglementation poids lourd pour [date]. */
    fun alertes(d: AppData, date: LocalDate): List<Alerte> {
        val l = ArrayList<Alerte>()
        val jour = conduite(d, date)
        if (jour > PAUSE_APRES) l += Alerte(Niveau.INFO, "Plus de 4h30 de conduite : 45 min de pause obligatoire (ou 15 + 30 min).")
        val lun = lundi(date)
        val joursSup9 = (0 until 7).map { lun.plusDays(it.toLong()) }.filter { conduite(d, it) > MAX_JOUR }
        when {
            jour > MAX_JOUR_ETENDU -> l += Alerte(Niveau.DEPASSEMENT, "Conduite de " + Heures.formatMinutes(jour) + " : au-delà du maximum de 10h par jour.")
            jour > MAX_JOUR -> {
                val rang = joursSup9.indexOf(date) + 1
                l += if (rang > 2) Alerte(Niveau.DEPASSEMENT, "${rang}e journée à plus de 9h cette semaine : 10h autorisées 2 fois par semaine seulement.")
                else Alerte(Niveau.ATTENTION, "Journée à plus de 9h ($rang/2 autorisées à 10h cette semaine).")
            }
        }
        val semaine = conduiteSemaine(d, date)
        if (semaine > MAX_SEMAINE) l += Alerte(Niveau.DEPASSEMENT, "Semaine à " + Heures.formatMinutes(semaine) + " de conduite : maximum 56h.")
        val deux = semaine + conduiteSemaine(d, lun.minusDays(7))
        if (deux > MAX_DEUX_SEMAINES) l += Alerte(Niveau.DEPASSEMENT, "Deux semaines à " + Heures.formatMinutes(deux) + " de conduite : maximum 90h.")
        return l
    }
}
