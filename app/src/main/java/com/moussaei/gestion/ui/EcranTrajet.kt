package com.moussaei.gestion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moussaei.gestion.core.ARRET_CHANTIER
import com.moussaei.gestion.core.ARRET_FOURNISSEUR
import com.moussaei.gestion.core.ARRET_LIBRE
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.STATUT_TERMINE
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions

/** Bouton « ➕ Trajet » / « ✓ Dans le trajet » (re-toucher retire l'arrêt). */
@Composable
fun BoutonTrajet(data: AppData, repo: Repository, type: String, ref: String, nom: String, adresse: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dedans = Gestion.dansTrajet(data, type, ref)
    BoutonPetit(if (dedans) "✓ Dans le trajet" else "➕ Trajet", if (dedans) Couleurs.Vert else Couleurs.Indigo, modifier) {
        if (!dedans && adresse.isBlank()) { Actions.toast(ctx, "Pas d'adresse pour ce lieu"); return@BoutonPetit }
        repo.update { Gestion.basculerDansTrajet(it, type, ref, nom, adresse) }
        Actions.toast(ctx, if (dedans) "Retiré du trajet" else "Ajouté au trajet (" + (data.trajet.size + 1) + " arrêts)")
    }
}

@Composable
fun EcranTrajet(data: AppData, repo: Repository) {
    val ctx = LocalContext.current
    val confirmation = rememberConfirmation()
    var choixArrets by rememberSaveable { mutableStateOf(false) }
    var adresseLibre by rememberSaveable { mutableStateOf(false) }
    var choixDepart by rememberSaveable { mutableStateOf(false) }

    val arrets = Gestion.trajetAJour(data)
    val arrivee = Gestion.arrivee(data)
    val parties = Gestion.liensGoogleMaps(arrets, data.trajetDepart, arrivee)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Départ
        Carte {
            Text("Départ", fontSize = 12.sp, color = Couleurs.Gris)
            Text(
                if (data.trajetDepart.isBlank()) "📍 Ma position actuelle" else "🏁 " + data.trajetDepart,
                fontWeight = FontWeight.SemiBold,
            )
            BoutonPetit("Changer le départ", Couleurs.Gris) { choixDepart = true }
        }

        // Arrêts
        if (arrets.isEmpty()) {
            Vide("Aucun arrêt. Ajoute des chantiers, des fournisseurs ou une adresse.")
        } else {
            Text(
                "📍 ${arrets.size} arrêt" + (if (arrets.size > 1) "s" else "") + " · ~" + Gestion.dureeTexte(Gestion.minutesEstimees(arrets)) + " de trajet",
                fontWeight = FontWeight.Bold,
            )
            arrets.forEachIndexed { i, a ->
                Carte(lisere = if (a.type == ARRET_CHANTIER) Couleurs.Orange else if (a.type == ARRET_FOURNISSEUR) Couleurs.Bleu else Couleurs.Gris) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Text((i + 1).toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            val icone = when (a.type) { ARRET_CHANTIER -> "🏗️ "; ARRET_FOURNISSEUR -> "📦 "; else -> "📌 " }
                            Text(icone + a.nom, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            if (a.adresse != a.nom) LienAdresse(a.adresse)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PetitBouton("▲", i > 0) { repo.update { Gestion.deplacerArret(it, a.id, -1) } }
                            PetitBouton("▼", i < arrets.size - 1) { repo.update { Gestion.deplacerArret(it, a.id, 1) } }
                        }
                        PetitBouton("✕", true) { repo.update { Gestion.retirerDuTrajet(it, a.id) } }
                    }
                }
            }
        }

        // Arrivée : le dépôt
        Carte(lisere = Couleurs.Vert) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fin de journée", fontSize = 12.sp, color = Couleurs.Gris)
                    Text(
                        when {
                            data.reglages.depot.isBlank() -> "Dépôt non renseigné (Réglages)"
                            data.reglages.finirAuDepot -> "🏁 Retour au dépôt"
                            else -> "Pas de retour au dépôt"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (data.reglages.depot.isNotBlank()) Text(data.reglages.depot, fontSize = 12.sp, color = Couleurs.Gris)
                }
                if (data.reglages.depot.isNotBlank()) {
                    androidx.compose.material3.Switch(
                        checked = data.reglages.finirAuDepot,
                        onCheckedChange = { on -> repo.update { Gestion.modifierReglages(it, it.reglages.copy(finirAuDepot = on)) } },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonPetit("➕ Chantiers / fournisseurs", Couleurs.Indigo, Modifier.weight(1f)) { choixArrets = true }
            BoutonPetit("✏️ Autre adresse", Couleurs.Indigo, Modifier.weight(1f)) { adresseLibre = true }
        }

        if (parties.size == 1) {
            BoutonPlein("🚀 Ouvrir dans Google Maps", Couleurs.Vert) { Actions.ouvrirUrl(ctx, parties[0].url) }
        } else if (parties.size > 1) {
            Ligne("Google Maps accepte 10 arrêts par lien : ta tournée est découpée en ${parties.size} parties. Lance la partie suivante quand tu as fini la précédente.")
            parties.forEach { p ->
                BoutonPlein("🚀 Partie ${p.numero} : arrêts ${p.premier} à ${p.dernier}", Couleurs.Vert) { Actions.ouvrirUrl(ctx, p.url) }
            }
        }

        if (arrets.isNotEmpty()) {
            BoutonContour("🗑️ Vider le trajet") {
                confirmation.demander("Vider tout le trajet ?", "Effacer") { repo.update { Gestion.viderTrajet(it) } }
            }
        }
    }

    if (choixArrets) ChoixArrets(data, repo) { choixArrets = false }
    if (adresseLibre) DialogueAdresse(
        titre = "Ajouter une adresse",
        avecNom = true,
        onValider = { nom, adr ->
            repo.update { Gestion.ajouterAuTrajet(it, ARRET_LIBRE, "", nom, adr).first }
            adresseLibre = false
        },
        onFermer = { adresseLibre = false },
    )
    if (choixDepart) ChoixDepart(data, repo) { choixDepart = false }
    ConfirmationHost(confirmation)
}

@Composable
private fun PetitBouton(texte: String, actif: Boolean, onClick: () -> Unit) {
    Text(
        texte,
        fontSize = 16.sp,
        color = if (actif) MaterialTheme.colorScheme.primary else Color(0xFFCBD0D8),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = actif) { onClick() }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private data class Lieu(val type: String, val ref: String, val nom: String, val adresse: String)

/** Liste à cocher des chantiers en cours et des fournisseurs. */
@Composable
private fun ChoixArrets(data: AppData, repo: Repository, onFermer: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val lieux = (data.chantiers.filter { it.statut != STATUT_TERMINE }.map { Lieu(ARRET_CHANTIER, it.id, Gestion.nomAffiche(it), it.adresse) } +
        data.fournisseurs.map { Lieu(ARRET_FOURNISSEUR, it.id, it.nom.trim(), it.adresse) })
        .sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }

    FenetrePleine("➕ Ajouter au trajet", onFermer) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            placeholder = { Text("🔍 Rechercher chantier / fournisseur…") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Ligne("Coche les arrêts dans l'ordre de ta tournée (tu pourras le changer ensuite). ${data.trajet.size} arrêt(s) dans le trajet.")
        BoutonPlein("✓ Terminé", Couleurs.Vert) { onFermer() }
        lieux.filter { Gestion.correspond(q, it.nom, it.adresse) }.forEach { l ->
            val rang = data.trajet.indexOfFirst { it.type == l.type && it.ref == l.ref }
            val coche = rang >= 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (coche) Couleurs.VertClair else MaterialTheme.colorScheme.surface)
                    .clickable(enabled = l.adresse.isNotBlank()) { repo.update { Gestion.basculerDansTrajet(it, l.type, l.ref, l.nom, l.adresse) } }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = coche,
                    enabled = l.adresse.isNotBlank(),
                    onCheckedChange = { repo.update { d -> Gestion.basculerDansTrajet(d, l.type, l.ref, l.nom, l.adresse) } },
                )
                Text(if (l.type == ARRET_CHANTIER) "🏗️" else "📦", fontSize = 18.sp)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(l.nom, fontWeight = FontWeight.SemiBold, color = if (coche) Couleurs.VertFonce else Color.Unspecified)
                    Text(l.adresse.ifBlank { "(pas d'adresse)" }, fontSize = 12.sp, color = Couleurs.Gris)
                }
                if (coche) Text("${rang + 1}", fontWeight = FontWeight.Bold, color = Couleurs.VertFonce, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

@Composable
private fun ChoixDepart(data: AppData, repo: Repository, onFermer: () -> Unit) {
    var saisie by rememberSaveable { mutableStateOf(false) }
    var liste by rememberSaveable { mutableStateOf(false) }
    FenetrePleine("Départ du trajet", onFermer) {
        BoutonPlein("📍 Ma position actuelle", Couleurs.Vert) {
            repo.update { Gestion.changerDepart(it, "") }
            onFermer()
        }
        Ligne("Google Maps part alors de l'endroit où tu es quand tu lances le trajet.")
        BoutonPlein("🏭 Un chantier ou un fournisseur", Couleurs.Indigo) { liste = true }
        BoutonPlein("✏️ Taper une adresse", Couleurs.Indigo) { saisie = true }
    }
    if (liste) {
        val options = (data.fournisseurs.map { Option("f" + it.id, it.nom.trim(), it.adresse) } +
            data.chantiers.filter { it.statut != STATUT_TERMINE }.map { Option("c" + it.id, Gestion.nomAffiche(it), it.adresse) })
            .filter { it.detail.isNotBlank() }
            .sortedWith { a, b -> Gestion.compareNoms(a.libelle, b.libelle) }
        ChoixDialog("Départ", options, onChoix = { id ->
            val o = options.firstOrNull { it.id == id }
            if (o != null) repo.update { Gestion.changerDepart(it, o.detail) }
            liste = false
            onFermer()
        }, onFermer = { liste = false })
    }
    if (saisie) DialogueAdresse(
        titre = "Adresse de départ",
        avecNom = false,
        valeurInitiale = data.trajetDepart,
        onValider = { _, adr ->
            repo.update { Gestion.changerDepart(it, adr) }
            saisie = false
            onFermer()
        },
        onFermer = { saisie = false },
    )
}

@Composable
private fun DialogueAdresse(
    titre: String,
    avecNom: Boolean,
    valeurInitiale: String = "",
    onValider: (String, String) -> Unit,
    onFermer: () -> Unit,
) {
    val ctx = LocalContext.current
    var nom by rememberSaveable { mutableStateOf("") }
    var adresse by rememberSaveable { mutableStateOf(valeurInitiale) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(titre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (avecNom) Champ("Nom (optionnel)", nom, { nom = it }, "ex: Point P, client…")
                Champ("Adresse *", adresse, { adresse = it }, "ex: 12 rue de la Paix, Paris")
            }
        },
        confirmButton = {
            Button(
                onClick = { if (adresse.isBlank()) Actions.toast(ctx, "Écris une adresse") else onValider(nom.trim(), adresse.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Couleurs.Vert, contentColor = Color.White),
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}
