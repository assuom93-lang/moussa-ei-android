package com.moussaei.gestion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.moussaei.gestion.core.Heures
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.time.LocalDate

@Composable
fun EcranAccueil(data: AppData, repo: Repository, onOuvrir: (Ecran, String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("🔍 Chercher une adresse, un chantier, une palette…") },
            singleLine = true,
            trailingIcon = if (q.isNotEmpty()) ({ Text("✕", modifier = Modifier.clickable { q = "" }.padding(8.dp)) }) else null,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (q.isNotBlank()) {
            ResultatsRecherche(data, repo, q, onOuvrir)
        } else {
            Resume(data, onOuvrir)
            Tuiles(data, repo, onOuvrir)
        }
    }
}

@Composable
private fun ResultatsRecherche(data: AppData, repo: Repository, q: String, onOuvrir: (Ecran, String) -> Unit) {
    val ctx = LocalContext.current
    var agences by rememberSaveable { mutableStateOf<String?>(null) }
    agences?.let { e -> AgencesDialog(data, repo, e) { agences = null } }
    val res = Gestion.rechercher(data, q)
    if (res.isEmpty()) { Vide("Rien trouvé pour « $q »"); return }
    Ligne("${res.size} résultat" + (if (res.size > 1) "s" else ""))
    res.forEach { r ->
        val (icone, ecran) = when (r.type) {
            Gestion.TypeResultat.CHANTIER -> "🏗️" to Ecran.CHANTIERS
            Gestion.TypeResultat.FOURNISSEUR -> "📦" to Ecran.FOURNISSEURS
            Gestion.TypeResultat.PALETTE -> "🪵" to Ecran.PALETTES
        }
        Carte(onClick = { onOuvrir(ecran, r.titre) }) {
            TitreCarte("$icone ${r.titre}")
            if (r.detail.isNotBlank()) Ligne(r.detail)
            LienAdresse(r.adresse)
            if (r.type == Gestion.TypeResultat.PALETTE && r.adresse.isBlank() && r.enseigne.isNotBlank()) {
                BoutonPetit("🏬 Voir les agences ${r.enseigne} (📍 / ➕ Trajet)", Couleurs.Bleu, Modifier.fillMaxWidth().padding(top = 4.dp)) { agences = r.enseigne }
            } else if (r.adresse.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonPetit("📍 Maps", Couleurs.Bleu, Modifier.weight(1f)) { Actions.ouvrirAdresse(ctx, r.adresse) }
                    if (r.refArret.isNotBlank()) {
                        BoutonTrajet(data, repo, r.typeArret, r.refArret, r.nomArret, r.adresse, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Resume(data: AppData, onOuvrir: (Ecran, String) -> Unit) {
    val aujourdhui = LocalDate.now()
    val trajet = Gestion.trajetAJour(data)
    val aCharger = Gestion.nbACharger(data)
    val jour = Heures.dernierJourARemplir(data.heures, aujourdhui)
    val heuresOk = data.heures[jour.toString()]?.remplie == true || !Heures.estOuvre(jour)

    Carte {
        Text("Aujourd'hui", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        LigneResume(
            "🗺️",
            if (trajet.isEmpty()) "Aucun trajet préparé"
            else "Trajet : ${trajet.size} arrêt" + (if (trajet.size > 1) "s" else "") + " · ~" + Gestion.dureeTexte(Gestion.minutesEstimees(trajet)),
        ) { onOuvrir(Ecran.TRAJET, "") }
        LigneResume("🚚", if (aCharger == 0) "Rien à charger" else "$aCharger matériel" + (if (aCharger > 1) "s" else "") + " à charger") { onOuvrir(Ecran.CAMION, "") }
        if (data.reglages.entreprise.isBlank() || data.reglages.depot.isBlank()) {
            LigneResume("🏢", "Indique ton entreprise et ton dépôt", couleur = Couleurs.OrangeFonce) { onOuvrir(Ecran.REGLAGES, "") }
        }
        val nbRendre = Gestion.nbARendre(data)
        if (nbRendre > 0) {
            LigneResume(
                "↩",
                "$nbRendre palette" + (if (nbRendre > 1) "s" else "") + " à rendre : " +
                    Gestion.aRendreParEnseigne(data).joinToString(", ") { (e, l) -> e + " " + l.sumOf { it.quantite } },
                couleur = Couleurs.OrangeFonce,
            ) { onOuvrir(Ecran.PALETTES, "") }
        }
        LigneResume(
            "⏰",
            if (heuresOk) "Heures à jour" else "Heures à remplir : " + Heures.titreJour(jour),
            couleur = if (heuresOk) Color.Unspecified else Couleurs.RougeFonce,
        ) { onOuvrir(Ecran.HEURES, "") }
    }
}

@Composable
private fun LigneResume(icone: String, texte: String, couleur: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icone, fontSize = 18.sp)
        Text("  $texte", fontSize = 14.sp, color = couleur, modifier = Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = Couleurs.Gris)
    }
}

@Composable
private fun Tuiles(data: AppData, repo: Repository, onOuvrir: (Ecran, String) -> Unit) {
    val nbTrajet = data.trajet.size
    // Trajet en grand : c'est la fonction la plus utilisée.
    Tuile(
        Ecran.TRAJET, if (nbTrajet == 0) "Préparer ma tournée" else "$nbTrajet arrêt" + (if (nbTrajet > 1) "s" else ""),
        Couleurs.Bleu, Color.White, Modifier.fillMaxWidth(), grande = true,
    ) { onOuvrir(Ecran.TRAJET, "") }

    val tuiles = listOf(
        Triple(Ecran.CHANTIERS, "${data.chantiers.count { it.statut != com.moussaei.gestion.core.STATUT_TERMINE }} en cours", Couleurs.OrangeClair to Couleurs.OrangeFonce),
        Triple(Ecran.FOURNISSEURS, "${data.fournisseurs.size}", Couleurs.BleuClair to Couleurs.Bleu),
        Triple(Ecran.PALETTES, Gestion.nbARendre(data).let { if (it > 0) "$it à rendre" else "${data.palettes.size} types" }, FondPalette to CouleurPalette),
        Triple(Ecran.CAMION, Gestion.nbACharger(data).let { if (it == 0) "vide" else "$it à charger" }, Couleurs.RougeClair to Couleurs.RougeFonce),
        Triple(Ecran.HEURES, Heures.format(Heures.totalMois(data.heures, LocalDate.now().year, LocalDate.now().monthValue).total) + " ce mois", Couleurs.VertClair to Couleurs.VertFonce),
        Triple(Ecran.HISTORIQUE, "${data.archives.size} livrés", Couleurs.GrisClair to Couleurs.Gris),
        Triple(Ecran.LEXIQUE, "${repo.catalogue.size} agences IDF", Couleurs.BleuClair to Couleurs.Bleu),
    )
    tuiles.chunked(2).forEach { ligne ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ligne.forEach { (e, info, couleurs) ->
                Tuile(e, info, couleurs.first, couleurs.second, Modifier.weight(1f)) { onOuvrir(e, "") }
            }
            if (ligne.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tuile(e: Ecran, info: String, fond: Color, couleur: Color, modifier: Modifier, grande: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = if (grande) 96.dp else 104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fond)
            .clickable { onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (grande) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (grande) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.icone, fontSize = 34.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(e.titre, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = couleur)
                    Text(info, fontSize = 13.sp, color = couleur.copy(alpha = 0.85f))
                }
            }
        } else {
            Text(e.icone, fontSize = 30.sp)
            Text(e.titre, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = couleur, textAlign = TextAlign.Center)
            Text(info, fontSize = 12.sp, color = couleur.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        }
    }
}
