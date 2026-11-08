package com.moussaei.gestion.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.roundToInt

/** Règles de l'onglet Heures (identiques à la v3.21 : pause fixe d'1 h déduite). */
object Heures {

    const val PAUSE_H = 1.0

    val JOURS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
    val MOIS = listOf("Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre")

    fun jour(d: LocalDate): String = JOURS[d.dayOfWeek.value - 1]
    fun mois(m: Int): String = MOIS[m - 1]

    /** "Lundi 3 novembre" */
    fun titreJour(d: LocalDate): String = jour(d) + " " + d.dayOfMonth + " " + mois(d.monthValue).lowercase()

    fun estOuvre(d: LocalDate): Boolean = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    /** Pas de travail le week-end : samedi / dimanche → vendredi précédent. */
    fun jourOuvre(d: LocalDate): LocalDate = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> d.minusDays(1)
        DayOfWeek.SUNDAY -> d.minusDays(2)
        else -> d
    }

    /** Jour ouvré suivant (sens = 1) ou précédent (sens = -1), en sautant le week-end. */
    fun decalerOuvre(d: LocalDate, sens: Int): LocalDate {
        var x = d.plusDays(sens.toLong())
        while (!estOuvre(x)) x = x.plusDays(sens.toLong())
        return x
    }

    fun parseHeure(s: String): LocalTime? = try {
        val p = s.trim().split(":")
        LocalTime.of(p[0].toInt(), p.getOrElse(1) { "0" }.toInt())
    } catch (e: Exception) { null }

    /** Heures travaillées (pause déduite), null si CP ou fin non saisie. */
    fun total(h: Heure?): Double? {
        if (h == null || h.cp || h.fin.isBlank()) return null
        val d = parseHeure(h.debut.ifBlank { Heure.DEBUT_PAR_DEFAUT }) ?: return null
        val f = parseHeure(h.fin) ?: return null
        val t = (f.hour + f.minute / 60.0) - (d.hour + d.minute / 60.0) - PAUSE_H
        return maxOf(0.0, (t * 100).roundToInt() / 100.0)
    }

    /** 7.5 -> "7h30", 8.0 -> "8h", null -> "—" */
    fun format(x: Double?): String {
        if (x == null) return "—"
        var h = floor(x).toInt()
        var m = ((x - h) * 60).roundToInt()
        if (m == 60) { h++; m = 0 }
        return if (m != 0) h.toString() + "h" + m.toString().padStart(2, '0') else h.toString() + "h"
    }

    /**
     * Plus ancien jour ouvré non rempli de la série qui remonte depuis aujourd'hui
     * (14 jours max). Si rien n'est saisi du tout, ou si aujourd'hui est rempli : aujourd'hui.
     */
    fun dernierJourARemplir(heures: Map<String, Heure>, aujourdhui: LocalDate): LocalDate {
        if (heures.isEmpty()) return aujourdhui
        var trouve: LocalDate? = null
        for (i in 0 until 14) {
            val d = aujourdhui.minusDays(i.toLong())
            if (!estOuvre(d)) continue
            val h = heures[d.toString()]
            if (h == null || !h.remplie) trouve = d else break
        }
        return trouve ?: aujourdhui
    }

    enum class Etat { OK, CP, AUJOURDHUI, MANQUANT, FUTUR, REPOS }

    fun etat(heures: Map<String, Heure>, d: LocalDate, aujourdhui: LocalDate): Etat {
        val h = heures[d.toString()]
        return when {
            h != null && h.cp -> Etat.CP
            h != null && h.fin.isNotBlank() -> Etat.OK
            !estOuvre(d) -> Etat.REPOS
            d == aujourdhui -> Etat.AUJOURDHUI
            d.isBefore(aujourdhui) -> Etat.MANQUANT
            else -> Etat.FUTUR
        }
    }

    data class TotalMois(val total: Double, val jours: Int, val cp: Int)

    fun totalMois(heures: Map<String, Heure>, annee: Int, mois: Int): TotalMois {
        var t = 0.0
        var n = 0
        var cp = 0
        heures.forEach { (k, h) ->
            val d = try { LocalDate.parse(k) } catch (e: Exception) { return@forEach }
            if (d.year == annee && d.monthValue == mois) {
                if (h.cp) cp++ else total(h)?.let { t += it; n++ }
            }
        }
        return TotalMois((t * 100).roundToInt() / 100.0, n, cp)
    }

    /** Récap texte du mois à partager (WhatsApp, SMS...). */
    fun recapMois(heures: Map<String, Heure>, annee: Int, mois: Int): String {
        val l = ArrayList<String>()
        l += "Heures " + mois(mois) + " " + annee
        l += ""
        var tot = 0.0
        val premier = LocalDate.of(annee, mois, 1)
        for (j in 1..premier.lengthOfMonth()) {
            val d = LocalDate.of(annee, mois, j)
            val h = heures[d.toString()]
            if (h == null && !estOuvre(d)) continue
            val lbl = jour(d).take(3) + " " + j.toString().padStart(2, '0')
            val note = if (h != null && h.note.isNotBlank()) " (" + h.note + ")" else ""
            l += when {
                h != null && h.cp -> "$lbl : CP$note"
                h != null && h.fin.isNotBlank() -> {
                    val x = total(h) ?: 0.0
                    tot += x
                    lbl + " : " + h.debut + " → " + h.fin + " = " + format(x) + note
                }
                else -> "$lbl : (non rempli)"
            }
        }
        val tm = totalMois(heures, annee, mois)
        l += ""
        l += "TOTAL : " + format((tot * 100).roundToInt() / 100.0) + " sur " + tm.jours + " jour" + (if (tm.jours > 1) "s" else "") +
            (if (tm.cp > 0) " · " + tm.cp + " CP" else "")
        return l.joinToString("\n")
    }
}
