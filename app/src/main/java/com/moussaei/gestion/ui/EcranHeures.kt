package com.moussaei.gestion.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Heure
import com.moussaei.gestion.core.Heures
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.time.LocalDate
import java.time.LocalTime

private fun styleEtat(e: Heures.Etat): Pair<Color, Color> = when (e) {
    Heures.Etat.OK -> Couleurs.VertClair to Couleurs.VertFonce
    Heures.Etat.CP -> Couleurs.OrangeClair to Couleurs.OrangeFonce
    Heures.Etat.AUJOURDHUI -> Couleurs.BleuClair to Couleurs.Bleu
    Heures.Etat.MANQUANT -> Couleurs.RougeClair to Couleurs.RougeFonce
    Heures.Etat.FUTUR -> Couleurs.GrisClair to Color(0xFF9CA3AF)
    Heures.Etat.REPOS -> Color.Transparent to Color(0xFFBFC4CC)
}

@Composable
fun EcranHeures(data: AppData, repo: Repository) {
    val aujourdhui = LocalDate.now()
    var mode by rememberSaveable { mutableStateOf("jour") }
    var dateTxt by rememberSaveable { mutableStateOf(Heures.jourOuvre(Heures.dernierJourARemplir(data.heures, aujourdhui)).toString()) }
    var edition by rememberSaveable { mutableStateOf<String?>(null) }
    val date = LocalDate.parse(dateTxt)

    fun aller(d: LocalDate) { dateTxt = d.toString() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (mode) {
            "mois" -> VueMois(data, date, aujourdhui,
                onNav = { aller(date.withDayOfMonth(1).plusMonths(it.toLong())) },
                onJour = { aller(Heures.jourOuvre(it)); mode = "jour" },
                onAnnee = { mode = "annee" },
                onDernier = { aller(Heures.jourOuvre(Heures.dernierJourARemplir(data.heures, aujourdhui))); mode = "jour" },
            )
            "annee" -> VueAnnee(data, date,
                onNav = { aller(LocalDate.of(date.year + it, 1, 1)) },
                onMois = { m -> aller(LocalDate.of(date.year, m, 1)); mode = "mois" },
                onDernier = { aller(Heures.jourOuvre(Heures.dernierJourARemplir(data.heures, aujourdhui))); mode = "jour" },
            )
            else -> VueJour(data, date, aujourdhui,
                onNav = { aller(Heures.decalerOuvre(date, it)) },
                onJour = { aller(it) },
                onEditer = { edition = date.toString() },
                onMois = { mode = "mois" },
                onDernier = { aller(Heures.jourOuvre(Heures.dernierJourARemplir(data.heures, aujourdhui))) },
            )
        }
    }

    edition?.let { k ->
        EditionHeure(LocalDate.parse(k), data.heures[k], repo) { edition = null }
    }
}

@Composable
private fun EnteteNav(titre: String, sousTitre: String, onNav: (Int) -> Unit, onTitre: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BoutonPetit("◀", Couleurs.Bleu) { onNav(-1) }
        Column(
            Modifier.weight(1f).then(if (onTitre != null) Modifier.clickable { onTitre() } else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(titre, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Text(sousTitre, fontSize = 12.sp, color = Couleurs.Gris, textAlign = TextAlign.Center)
        }
        BoutonPetit("▶", Couleurs.Bleu) { onNav(1) }
    }
}

@Composable
private fun VueJour(
    data: AppData, d: LocalDate, aujourdhui: LocalDate,
    onNav: (Int) -> Unit, onJour: (LocalDate) -> Unit, onEditer: () -> Unit, onMois: () -> Unit, onDernier: () -> Unit,
) {
    val h = data.heures[d.toString()]
    val etat = Heures.etat(data.heures, d, aujourdhui)
    val tm = Heures.totalMois(data.heures, d.year, d.monthValue)

    EnteteNav(Heures.titreJour(d), Heures.mois(d.monthValue) + " " + d.year + " · " + Heures.format(tm.total) + " ce mois", onNav, onMois)

    Carte(onClick = onEditer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("⏰ Journée", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            when (etat) {
                Heures.Etat.OK -> Pastille("✅ Rempli", Couleurs.VertClair, Couleurs.VertFonce)
                Heures.Etat.CP -> Pastille("🏖️ Congé", Couleurs.OrangeClair, Couleurs.OrangeFonce)
                Heures.Etat.MANQUANT -> Pastille("⚠️ À remplir", Couleurs.RougeClair, Couleurs.RougeFonce)
                Heures.Etat.AUJOURDHUI -> Pastille("📅 Aujourd'hui", Couleurs.BleuClair, Couleurs.Bleu)
                Heures.Etat.REPOS -> Pastille("Week-end", Couleurs.GrisClair, Couleurs.Gris)
                Heures.Etat.FUTUR -> {}
            }
        }
        Spacer(Modifier.heightIn(min = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CaseHeure("Début", if (h != null && h.debut.isNotBlank() && !h.cp) h.debut else Heure.DEBUT_PAR_DEFAUT, false, Modifier.weight(1f))
            val finTxt = when {
                h?.cp == true -> "CP"
                h != null && h.fin.isNotBlank() -> h.fin
                else -> "--:--"
            }
            CaseHeure("Fin", finTxt, h == null || (!h.cp && h.fin.isBlank()), Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total (pause 1 h déduite)", fontSize = 13.sp, color = Couleurs.Gris, modifier = Modifier.weight(1f))
            Text(if (h?.cp == true) "CP" else Heures.format(Heures.total(h)), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        if (h != null && h.note.isNotBlank()) Ligne("📝 " + h.note)
        BoutonPlein("✏️ " + (if (h != null && h.remplie) "Modifier" else "Saisir la fin de journée")) { onEditer() }
    }

    // Semaine du lundi au vendredi
    val lundi = d.minusDays((d.dayOfWeek.value - 1).toLong())
    Carte {
        Text("Cette semaine", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 5) {
                val dd = lundi.plusDays(i.toLong())
                val e = Heures.etat(data.heures, dd, aujourdhui)
                val hh = data.heures[dd.toString()]
                val lbl = when {
                    hh?.cp == true -> "CP"
                    Heures.total(hh) != null -> Heures.format(Heures.total(hh))
                    e == Heures.Etat.MANQUANT -> "!"
                    else -> "–"
                }
                val (fond, couleur) = styleEtat(e)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(fond)
                        .then(if (dd == d) Modifier.border(2.dp, Couleurs.Bleu, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { onJour(dd) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Heures.jour(dd).take(3) + " " + dd.dayOfMonth, fontSize = 11.sp, color = couleur)
                    Text(lbl, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = couleur)
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoutonContour("📍 Dernier jour", Modifier.weight(1f)) { onDernier() }
        BoutonContour("📅 Vue mois", Modifier.weight(1f)) { onMois() }
    }
}

@Composable
private fun CaseHeure(libelle: String, valeur: String, enAttente: Boolean, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enAttente) Couleurs.BleuClair else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (enAttente) Modifier.border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(8.dp)) else Modifier)
            .padding(10.dp),
    ) {
        Text(libelle, fontSize = 12.sp, color = if (enAttente) Couleurs.Bleu else Couleurs.Gris)
        Text(valeur, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (enAttente) Color(0xFF3B82F6) else Color.Unspecified)
    }
}

@Composable
private fun VueMois(
    data: AppData, d: LocalDate, aujourdhui: LocalDate,
    onNav: (Int) -> Unit, onJour: (LocalDate) -> Unit, onAnnee: () -> Unit, onDernier: () -> Unit,
) {
    val ctx = LocalContext.current
    val tm = Heures.totalMois(data.heures, d.year, d.monthValue)
    EnteteNav(Heures.mois(d.monthValue) + " " + d.year, d.year.toString() + " ▾", onNav, onAnnee)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Resume("Total", Heures.format(tm.total), Couleurs.VertClair, Couleurs.VertFonce, Modifier.weight(1f))
        Resume("Jours", tm.jours.toString(), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        Resume("CP", tm.cp.toString(), Couleurs.OrangeClair, Couleurs.OrangeFonce, Modifier.weight(1f))
    }
    Carte {
        // Du lundi au vendredi seulement : pas de travail le week-end.
        val premier = d.withDayOfMonth(1)
        val jours = (1..premier.lengthOfMonth()).map { premier.withDayOfMonth(it) }.filter { Heures.estOuvre(it) }
        val decalage = jours.first().dayOfWeek.value - 1
        val cases: List<LocalDate?> = List(decalage) { null } + jours
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "J", "V").forEach {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Couleurs.Gris, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cases.chunked(5).forEach { semaine ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 5) {
                    val dd = semaine.getOrNull(i)
                    if (dd == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val e = Heures.etat(data.heures, dd, aujourdhui)
                        val hh = data.heures[dd.toString()]
                        val lbl = if (hh?.cp == true) "CP" else Heures.total(hh)?.let { Heures.format(it) } ?: ""
                        val (fond, couleur) = styleEtat(e)
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(fond)
                                .clickable { onJour(dd) }
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(dd.dayOfMonth.toString(), fontSize = 12.sp, color = couleur)
                            Text(lbl, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = couleur)
                        }
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoutonContour("📍 Dernier jour", Modifier.weight(1f)) { onDernier() }
        BoutonPlein("📤 Export mois", modifier = Modifier.weight(1f)) {
            val ent = data.reglages.entreprise
            Actions.partagerTexte(
                ctx,
                "Heures " + Heures.mois(d.monthValue) + " " + d.year + (if (ent.isNotBlank()) " — $ent" else ""),
                (if (ent.isNotBlank()) "$ent\n" else "") + Heures.recapMois(data.heures, d.year, d.monthValue),
            )
        }
    }
}

@Composable
private fun Resume(libelle: String, valeur: String, fond: Color, couleur: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(fond).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(libelle, fontSize = 11.sp, color = couleur)
        Text(valeur, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = couleur)
    }
}

@Composable
private fun VueAnnee(data: AppData, d: LocalDate, onNav: (Int) -> Unit, onMois: (Int) -> Unit, onDernier: () -> Unit) {
    val totaux = (1..12).map { Heures.totalMois(data.heures, d.year, it) }
    val total = totaux.sumOf { it.total }
    EnteteNav(d.year.toString(), Heures.format(Math.round(total * 100) / 100.0) + " sur l'année", onNav)
    Carte {
        (1..12).chunked(3).forEach { ligne ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { m ->
                    val tm = totaux[m - 1]
                    val rempli = tm.jours > 0 || tm.cp > 0
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (rempli) Couleurs.BleuClair else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onMois(m) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val c = if (rempli) Couleurs.Bleu else Color(0xFF9CA3AF)
                        Text(Heures.mois(m).take(4), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c)
                        Text(if (rempli) Heures.format(tm.total) else "–", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c)
                        if (tm.cp > 0) Text("${tm.cp} CP", fontSize = 10.sp, color = c)
                    }
                }
            }
        }
    }
    BoutonContour("📍 Dernier jour") { onDernier() }
}

@Composable
private fun EditionHeure(date: LocalDate, h: Heure?, repo: Repository, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val confirmation = rememberConfirmation()
    var debut by rememberSaveable { mutableStateOf(if (h != null && !h.cp && h.debut.isNotBlank()) h.debut else Heure.DEBUT_PAR_DEFAUT) }
    var fin by rememberSaveable { mutableStateOf(if (h?.cp == true) "" else h?.fin ?: "") }
    var note by rememberSaveable { mutableStateOf(h?.note ?: "") }

    fun choisir(valeur: String, defaut: LocalTime, onOk: (String) -> Unit) {
        val t = Heures.parseHeure(valeur) ?: defaut
        TimePickerDialog(ctx, { _, hh, mm -> onOk("%02d:%02d".format(hh, mm)) }, t.hour, t.minute, true).show()
    }

    FenetrePleine("⏰ " + Heures.titreJour(date), onFermer) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).clickable { choisir(debut, LocalTime.of(6, 30)) { debut = it } }) {
                CaseHeure("Début (toucher pour changer)", debut, false, Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f).clickable { choisir(fin, LocalTime.now().withSecond(0)) { fin = it } }) {
                CaseHeure("Fin (toucher pour changer)", fin.ifBlank { "--:--" }, fin.isBlank(), Modifier.fillMaxWidth())
            }
        }
        val apercu = Heures.total(Heure(debut, fin))
        if (apercu != null) Ligne("Total : " + Heures.format(apercu) + " (pause 1 h déduite)", MaterialTheme.colorScheme.onSurface)
        Champ("Note (optionnel)", note, { note = it }, "ex: déplacement, heures sup…")
        Ligne("Pause fixe de 1 h déduite automatiquement.")
        BoutonPlein("✅ Enregistrer", Couleurs.Vert) {
            if (fin.isBlank()) { Actions.toast(ctx, "❌ Indique l'heure de fin"); return@BoutonPlein }
            repo.update { Gestion.enregistrerHeure(it, date, Heure(debut, fin, false, note.trim())) }
            onFermer()
        }
        BoutonPlein("🏖️ Marquer congé (CP)", Couleurs.Orange) {
            repo.update { Gestion.enregistrerHeure(it, date, Heure("", "", true, note.trim())) }
            onFermer()
        }
        if (h != null) BoutonPlein("🗑️ Effacer ce jour", Couleurs.Rouge) {
            confirmation.demander("Effacer les heures de ce jour ?", "Effacer") {
                repo.update { Gestion.supprimerHeure(it, date) }
                onFermer()
            }
        }
    }
    ConfirmationHost(confirmation)
}
