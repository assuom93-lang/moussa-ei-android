package com.moussaei.gestion.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moussaei.gestion.core.ARRET_CHANTIER
import com.moussaei.gestion.core.ARRET_FOURNISSEUR
import com.moussaei.gestion.core.ARRET_LIBRE
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Heures
import com.moussaei.gestion.core.STATUT_TERMINE
import com.moussaei.gestion.core.TrajetEnregistre
import com.moussaei.gestion.core.Trajets
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import com.moussaei.gestion.util.CalculPL
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

fun choisirDate(ctx: Context, date: LocalDate, onOk: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, a, m, j -> onOk(LocalDate.of(a, m + 1, j)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}

fun heureMaintenant(): String = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

private fun dateLisible(t: TrajetEnregistre): String =
    try { Heures.titreJour(LocalDate.parse(t.date)) } catch (e: Exception) { t.date }

/** "12 arrêts · 84 km · 2h10 de route PL" */
fun resumeTrajet(t: TrajetEnregistre): String {
    val n = t.arrets.size.toString() + " arrêt" + (if (t.arrets.size > 1) "s" else "")
    return if (t.calcule) n + " · " + Trajets.km(t.distanceM) + " · 🚛 " + Heures.formatMinutes(t.dureeS / 60)
    else "$n · temps de route pas encore calculé"
}

/** Enregistrer le trajet en cours : nom + jour travaillé. */
@Composable
fun DialogueEnregistrerTrajet(data: AppData, repo: Repository, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var dateTxt by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateTxt)
    var nom by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("💾 Enregistrer l'itinéraire") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChampChoix("Jour travaillé", Heures.titreJour(date) + " " + date.year) { choisirDate(ctx, date) { dateTxt = it.toString() } }
                Champ("Nom", nom, { nom = it }, Trajets.nomParDefaut(data, date))
                Ligne("Retrouve-le dans « 📚 Mes trajets » et dans les heures de ce jour.")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var id = ""
                    repo.update { d -> Trajets.enregistrer(d, date, heureMaintenant(), nom.trim()).also { id = it.second }.first }
                    CalculPL.calculerEnFond(ctx, repo, id)
                    Actions.toast(ctx, "✅ Itinéraire enregistré")
                    onFermer()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Couleurs.Vert, contentColor = Color.White),
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
private fun LigneTrajet(t: TrajetEnregistre, avecDate: Boolean, onClick: () -> Unit) {
    Carte(lisere = if (t.calcule) Couleurs.Vert else Couleurs.Gris, onClick = onClick) {
        Text("🗺️ " + t.nom, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (avecDate) Text(dateLisible(t) + (if (t.heure.isNotBlank()) " · " + t.heure else ""), fontSize = 12.sp, color = Couleurs.Gris)
        Text(resumeTrajet(t), fontSize = 13.sp)
        val apercu = t.arrets.take(4).joinToString(" → ") { it.nom } + if (t.arrets.size > 4) " → …" else ""
        if (apercu.isNotBlank()) Text(apercu, fontSize = 12.sp, color = Couleurs.Gris)
    }
}

/** Historique de tous les itinéraires enregistrés. */
@Composable
fun MesTrajets(data: AppData, repo: Repository, onFermer: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var fiche by rememberSaveable { mutableStateOf<String?>(null) }
    FenetrePleine("📚 Mes trajets (${data.trajetsEnregistres.size})", onFermer) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            placeholder = { Text("🔍 Nom, arrêt, date (2026-11)…") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        val liste = Trajets.historique(data, q)
        if (liste.isEmpty()) {
            Vide(
                if (data.trajetsEnregistres.isEmpty()) "Aucun itinéraire enregistré. Il est enregistré tout seul quand tu touches « Ouvrir dans Google Maps », ou avec « 💾 Enregistrer »."
                else "Aucun trajet ne correspond",
            )
        }
        var mois = ""
        liste.forEach { t ->
            val m = t.date.take(7)
            if (m != mois) {
                mois = m
                val titre = try { Heures.mois(m.substring(5, 7).toInt()) + " " + m.take(4) } catch (e: Exception) { m }
                SectionTitre(titre)
            }
            LigneTrajet(t, true) { fiche = t.id }
        }
    }
    fiche?.let { FicheTrajet(data, repo, it, onFermer = { fiche = null }, onRefait = { fiche = null; onFermer() }) }
}

private data class Etape(val libelle: String, val titre: String, val adresse: String, val couleur: Color)

private fun etapes(data: AppData, t: TrajetEnregistre): List<Etape> {
    val l = ArrayList<Etape>()
    val dep = Trajets.departCalcul(data, t)
    if (dep.isNotBlank() && (t.depart.isNotBlank() || data.reglages.depot.isNotBlank())) {
        l += Etape("D", if (t.depart.isBlank()) "Dépôt (prise de poste)" else "Départ", dep, Couleurs.Vert)
    }
    t.arrets.forEachIndexed { i, a ->
        l += Etape((i + 1).toString(), a.nom, a.adresse, if (a.type == ARRET_CHANTIER) Couleurs.Orange else Couleurs.Bleu)
    }
    if (t.arrivee.isNotBlank()) l += Etape("F", "Arrivée", t.arrivee, Couleurs.VertFonce)
    return l
}

/** Fiche d'un itinéraire : carte, arrêts, temps de route PL, partage, refaire, modifier. */
@Composable
fun FicheTrajet(data: AppData, repo: Repository, id: String, onFermer: () -> Unit, onRefait: () -> Unit = onFermer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmation = rememberConfirmation()
    val t = Trajets.parId(data, id)
    if (t == null) { LaunchedEffect(Unit) { onFermer() }; return }

    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf("") }
    var autoFait by remember { mutableStateOf(false) }
    var renommer by rememberSaveable { mutableStateOf(false) }
    var modifier by rememberSaveable { mutableStateOf(false) }
    var ajout by rememberSaveable { mutableStateOf(false) }
    var ajoutLibre by rememberSaveable { mutableStateOf(false) }
    var departArrivee by rememberSaveable { mutableStateOf(false) }
    val aCle = CalculPL.cle(ctx).isNotBlank()

    fun calculer() {
        if (enCours) return
        enCours = true; erreur = ""
        scope.launch {
            try { CalculPL.calculer(ctx, repo, id) } catch (e: Exception) { erreur = e.message ?: "Calcul impossible" }
            enCours = false
        }
    }
    LaunchedEffect(id) {
        if (!autoFait && !t.calcule && aCle) { autoFait = true; calculer() }
    }

    FenetrePleine("🗺️ " + t.nom, onFermer) {
        Text(dateLisible(t) + (if (t.heure.isNotBlank()) " · " + t.heure else ""), color = Couleurs.Gris, fontSize = 13.sp)

        // Carte
        val liste = etapes(data, t)
        val points = liste.mapNotNull { e -> Trajets.coordonnees(data, e.adresse)?.let { PointCarte(it, e.libelle, e.titre, e.couleur.toArgb()) } }
        val trace = remember(t.geometrie) { if (t.geometrie.isBlank()) emptyList() else Trajets.decoderPolyline(t.geometrie) }
        if (points.isNotEmpty() || trace.isNotEmpty()) {
            CarteTrajet(points, trace)
        } else {
            Note(if (aCle) "La carte s'affiche après le calcul du trajet." else "Ajoute ta clé OpenRouteService (gratuite) dans ⚙️ Réglages pour voir la carte et le temps de route poids lourd.")
        }

        // Temps de route
        Carte(lisere = if (t.calcule) Couleurs.Vert else Couleurs.Orange) {
            Text("🚛 Temps de route poids lourd", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            when {
                enCours -> Ligne("⏳ Calcul en cours…")
                t.calcule -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Heures.formatMinutes(t.dureeS / 60), fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.weight(1f))
                        Text(Trajets.km(t.distanceM), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Couleurs.Gris)
                    }
                    Ligne("Sans bouchons, ni temps aux arrêts. " + CalculPL.CAMION + ".")
                }
                else -> Ligne("Pas encore calculé.")
            }
            if (erreur.isNotBlank()) Ligne("❌ $erreur", Couleurs.RougeFonce)
            if (aCle && !enCours) BoutonPetit(if (t.calcule) "↻ Recalculer" else "🚛 Calculer le temps de route", Couleurs.Indigo) { calculer() }
        }

        // Étapes
        Carte {
            Text("Étapes", fontWeight = FontWeight.Bold)
            liste.forEachIndexed { i, e ->
                val troncon = if (i > 0) t.troncons.getOrNull(i - 1) else null
                if (troncon != null && troncon > 0) Text("   ↓ " + Heures.formatMinutes(maxOf(1, troncon / 60)), fontSize = 11.sp, color = Couleurs.Gris)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(e.couleur), contentAlignment = Alignment.Center) {
                        Text(e.libelle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(e.titre, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (e.adresse != e.titre) Text(e.adresse, fontSize = 12.sp, color = Couleurs.Gris)
                    }
                }
            }
            if (t.depart.isBlank()) Ligne("Départ « ma position » : le calcul part du dépôt.")
        }

        // Actions
        val liens = Gestion.liensGoogleMaps(t.arrets, t.depart, t.arrivee)
        liens.forEach { p ->
            BoutonPlein(if (liens.size > 1) "🚀 Google Maps partie ${p.numero}" else "🚀 Ouvrir dans Google Maps", Couleurs.Vert) { Actions.ouvrirUrl(ctx, p.url) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonPlein("📤 Partager", Couleurs.Bleu, Modifier.weight(1f)) { Actions.partagerTexte(ctx, t.nom, Trajets.texteAPartager(t)) }
            BoutonPlein("🔁 Refaire", Couleurs.Indigo, Modifier.weight(1f)) {
                val faire = {
                    repo.update { Trajets.refaire(it, id) }
                    Actions.toast(ctx, "✅ Trajet rechargé dans 🗺️ Trajet")
                    onRefait()
                }
                if (data.trajet.isNotEmpty()) confirmation.demander("Remplacer le trajet en cours par celui-ci ?", "Remplacer") { faire() } else faire()
            }
        }
        BoutonContour(if (modifier) "✓ Fin des modifications" else "✏️ Modifier l'itinéraire") { modifier = !modifier }

        if (modifier) {
            Carte(lisere = Couleurs.Indigo) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonPetit("✏️ Renommer", Couleurs.Indigo, Modifier.weight(1f)) { renommer = true }
                    BoutonPetit("📅 Changer de jour", Couleurs.Indigo, Modifier.weight(1f)) {
                        choisirDate(ctx, LocalDate.parse(t.date)) { d -> repo.update { Trajets.changerJour(it, id, d) }; Actions.toast(ctx, "✅ Déplacé au " + Heures.titreJour(d).lowercase()) }
                    }
                }
                BoutonPetit("🏁 Départ et arrivée", Couleurs.Indigo, Modifier.fillMaxWidth()) { departArrivee = true }
                Text("Arrêts", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                t.arrets.forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. " + a.nom, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        MiniBouton("▲", i > 0) { repo.update { Trajets.deplacerArret(it, id, a.id, -1) } }
                        MiniBouton("▼", i < t.arrets.size - 1) { repo.update { Trajets.deplacerArret(it, id, a.id, 1) } }
                        MiniBouton("✕", t.arrets.size > 1) { repo.update { Trajets.retirerArret(it, id, a.id) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonPetit("➕ Chantier / fournisseur", Couleurs.Indigo, Modifier.weight(1f)) { ajout = true }
                    BoutonPetit("✏️ Adresse", Couleurs.Indigo, Modifier.weight(1f)) { ajoutLibre = true }
                }
                Ligne("Après une modification, touche « Calculer » pour mettre à jour le temps de route.")
            }
        }

        BoutonContour("🗑️ Supprimer cet itinéraire") {
            confirmation.demander("Supprimer « ${t.nom} » ?") {
                repo.update { Trajets.supprimer(it, id) }
                onFermer()
            }
        }
    }

    if (renommer) {
        var nom by rememberSaveable { mutableStateOf(t.nom) }
        AlertDialog(
            onDismissRequest = { renommer = false },
            title = { Text("Renommer") },
            text = { Champ("Nom", nom, { nom = it }) },
            confirmButton = { TextButton(onClick = { repo.update { Trajets.renommer(it, id, nom) }; renommer = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { renommer = false }) { Text("Annuler") } },
        )
    }
    if (departArrivee) {
        var dep by rememberSaveable { mutableStateOf(t.depart) }
        var arr by rememberSaveable { mutableStateOf(t.arrivee) }
        val depot = data.reglages.depot
        AlertDialog(
            onDismissRequest = { departArrivee = false },
            title = { Text("Départ et arrivée") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Champ("Départ", dep, { dep = it }, "vide = ma position (calcul depuis le dépôt)")
                    Champ("Arrivée", arr, { arr = it }, "vide = pas de retour")
                    if (depot.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoutonPetit("Départ = dépôt", Couleurs.Gris, Modifier.weight(1f)) { dep = depot }
                        BoutonPetit("Arrivée = dépôt", Couleurs.Gris, Modifier.weight(1f)) { arr = depot }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { repo.update { Trajets.changerDepartArrivee(it, id, dep, arr) }; departArrivee = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { departArrivee = false }) { Text("Annuler") } },
        )
    }
    if (ajout) {
        val options = (data.chantiers.filter { it.statut != STATUT_TERMINE && it.adresse.isNotBlank() }.map { Option("c" + it.id, "🏗️ " + Gestion.nomAffiche(it), it.adresse) } +
            data.fournisseurs.filter { it.adresse.isNotBlank() }.map { Option("f" + it.id, "📦 " + it.nom.trim(), it.adresse) })
            .sortedWith { a, b -> Gestion.compareNoms(a.libelle.drop(3), b.libelle.drop(3)) }
        ChoixDialog("Ajouter un arrêt", options, onChoix = { oid ->
            val ref = oid.drop(1)
            if (oid.startsWith("c")) data.chantiers.firstOrNull { it.id == ref }?.let { c ->
                repo.update { Trajets.ajouterArret(it, id, ARRET_CHANTIER, c.id, Gestion.nomAffiche(c), c.adresse) }
            } else data.fournisseurs.firstOrNull { it.id == ref }?.let { f ->
                repo.update { Trajets.ajouterArret(it, id, ARRET_FOURNISSEUR, f.id, f.nom.trim(), f.adresse) }
            }
            ajout = false
        }, onFermer = { ajout = false })
    }
    if (ajoutLibre) DialogueAdresse(
        titre = "Ajouter une adresse",
        avecNom = true,
        onValider = { nom, adr -> repo.update { Trajets.ajouterArret(it, id, ARRET_LIBRE, "", nom, adr) }; ajoutLibre = false },
        onFermer = { ajoutLibre = false },
    )
    ConfirmationHost(confirmation)
}

@Composable
private fun MiniBouton(texte: String, actif: Boolean, onClick: () -> Unit) {
    Text(
        texte,
        fontSize = 16.sp,
        color = if (actif) MaterialTheme.colorScheme.primary else Color(0xFFCBD0D8),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = actif) { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Carte « Conduite » de la vue jour des heures : trajets du jour, temps de conduite, rappels. */
@Composable
fun CarteConduiteJour(data: AppData, date: LocalDate, onTrajet: (String) -> Unit, onSaisir: () -> Unit) {
    val trajets = Trajets.duJour(data, date)
    val saisie = data.heures[date.toString()]?.conduiteMin ?: -1
    val estimee = Trajets.conduiteEstimee(data, date)
    val conduite = Trajets.conduite(data, date)
    val semaine = Trajets.conduiteSemaine(data, date)
    val alertes = Trajets.alertes(data, date)
    Carte(lisere = when {
        alertes.any { it.niveau == Trajets.Niveau.DEPASSEMENT } -> Couleurs.Rouge
        alertes.any { it.niveau == Trajets.Niveau.ATTENTION } -> Couleurs.Orange
        else -> null
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🚛 Conduite", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text(if (conduite > 0) Heures.formatMinutes(conduite) else "–", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Ligne(
            when {
                saisie >= 0 -> "Relevé chrono" + if (estimee > 0) " (estimation trajets : " + Heures.formatMinutes(estimee) + ")" else ""
                estimee > 0 -> "Estimée d'après les trajets du jour, sans bouchons. Corrige avec ton relevé chrono."
                else -> "Aucun trajet calculé ce jour."
            },
        )
        Ligne("Semaine : " + Heures.formatMinutes(semaine) + " / 56h")
        alertes.forEach { a ->
            val (fond, couleur) = when (a.niveau) {
                Trajets.Niveau.DEPASSEMENT -> Couleurs.RougeClair to Couleurs.RougeFonce
                Trajets.Niveau.ATTENTION -> Couleurs.OrangeClair to Couleurs.OrangeFonce
                Trajets.Niveau.INFO -> Couleurs.BleuClair to Couleurs.Bleu
            }
            Text(
                (if (a.niveau == Trajets.Niveau.INFO) "ℹ️ " else "⚠️ ") + a.texte,
                fontSize = 13.sp, color = couleur,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(fond).padding(8.dp),
            )
        }
        BoutonPetit("⏱️ Saisir le relevé chrono", Couleurs.Indigo) { onSaisir() }
        if (trajets.isNotEmpty()) {
            Text("Trajets du jour", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            trajets.forEach { t ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onTrajet(t.id) }.padding(10.dp),
                ) {
                    Text("🗺️ " + t.nom + (if (t.heure.isNotBlank()) " · " + t.heure else ""), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(resumeTrajet(t), fontSize = 12.sp, color = Couleurs.Gris)
                }
            }
        }
    }
}
