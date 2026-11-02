package com.moussaei.gestion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.moussaei.gestion.core.EMPLACEMENT_CABINE
import com.moussaei.gestion.core.EMPLACEMENT_PLATEAU
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Materiel
import com.moussaei.gestion.core.STATUT_TERMINE
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.time.LocalDate

@Composable
fun EcranCamion(data: AppData, repo: Repository, recherche: String) {
    val confirmation = rememberConfirmation()
    var ficheId by rememberSaveable { mutableStateOf<String?>(null) }
    var formulaire by rememberSaveable { mutableStateOf<String?>(null) }
    var affectation by rememberSaveable { mutableStateOf<String?>(null) }
    var plusieurs by rememberSaveable { mutableStateOf(false) }

    val liste = Gestion.camionTrie(data).filter { m ->
        Gestion.correspond(
            recherche, m.nom, m.notes, Gestion.fournisseur(data, m.fournisseurId)?.nom,
            Gestion.chantiersDuMateriel(data, m.id).joinToString(" ") { Gestion.nomAffiche(it) },
        )
    }
    val aCharger = data.materiels.count { !it.recupere }
    val charges = data.materiels.size - aCharger
    val livresAuj = data.archives.count { it.dateLivraison == Gestion.dateFr(LocalDate.now()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Compteur("À charger", aCharger, Couleurs.RougeClair, Couleurs.RougeFonce, Modifier.weight(1f))
                Compteur("Chargés", charges, Couleurs.VertClair, Couleurs.VertFonce, Modifier.weight(1f))
                Compteur("Livrés auj.", livresAuj, Couleurs.BleuClair, Couleurs.Bleu, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPlein("➕ Un matériel", modifier = Modifier.weight(1f)) { formulaire = "" }
                BoutonPlein("📋 Plusieurs / scanner", Couleurs.Orange, Modifier.weight(1f)) { plusieurs = true }
            }
        }
        if (liste.isEmpty()) item { Vide(if (recherche.isBlank()) "Aucun matériel dans le camion" else "Aucun résultat") }
        items(liste, key = { it.id }) { m ->
            CarteMateriel(
                data, m,
                onClick = { ficheId = m.id },
                onEmplacement = { e -> repo.update { Gestion.basculerEmplacement(it, m.id, e) } },
                onCharge = { repo.update { Gestion.basculerCharge(it, m.id, LocalDate.now()) } },
                onChantier = { affectation = m.id },
                onLivre = {
                    confirmation.demander("Marquer comme livré et archiver ?", "Livré") {
                        repo.update { Gestion.livrer(it, m.id, LocalDate.now()) }
                    }
                },
                onSupprimer = { confirmation.demander("Supprimer ce matériel ?") { repo.update { Gestion.supprimerMateriel(it, m.id) } } },
            )
        }
    }

    ficheId?.let { id ->
        data.materiels.firstOrNull { it.id == id }?.let { m ->
            FicheMateriel(data, repo, m, onModifier = { formulaire = m.id }, onFermer = { ficheId = null })
        }
    }
    formulaire?.let { id -> FormulaireMateriel(data, repo, data.materiels.firstOrNull { it.id == id }) { formulaire = null } }
    affectation?.let { id -> data.materiels.firstOrNull { it.id == id }?.let { m -> AffectationChantiers(data, repo, m) { affectation = null } } }
    if (plusieurs) AjoutPlusieurs(data, repo) { plusieurs = false }
    ConfirmationHost(confirmation)
}

@Composable
private fun Compteur(libelle: String, n: Int, fond: Color, couleur: Color, modifier: Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(fond).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(n.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = couleur)
        Text(libelle, fontSize = 11.sp, color = couleur)
    }
}

@Composable
fun PastilleCharge(m: Materiel) {
    if (m.recupere) Pastille("🚛 Chargé" + (if (m.dateRecupere.isNotBlank()) " le " + m.dateRecupere else ""), Couleurs.VertClair, Couleurs.VertFonce)
    else Pastille("✕ À charger", Couleurs.RougeClair, Couleurs.RougeFonce)
}

@Composable
private fun CarteMateriel(
    data: AppData,
    m: Materiel,
    onClick: () -> Unit,
    onEmplacement: (String) -> Unit,
    onCharge: () -> Unit,
    onChantier: () -> Unit,
    onLivre: () -> Unit,
    onSupprimer: () -> Unit,
) {
    val chantiers = Gestion.chantiersDuMateriel(data, m.id)
    Carte(lisere = if (m.recupere) Couleurs.Vert else null, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) { TitreCarte(m.nom) }
            IconePhoto(com.moussaei.gestion.core.Produits.photoDe(data, m.nom))
        }
        Gestion.fournisseur(data, m.fournisseurId)?.let { Ligne("🏭 ${it.nom.trim()}") }
        if (chantiers.isNotEmpty()) Ligne("🏗️ → " + chantiers.joinToString(", ") { Gestion.nomAffiche(it) })
        Note(m.notes)
        Pastilles {
            PastilleCharge(m)
            if (m.emplacement == EMPLACEMENT_CABINE) Pastille("🚗 Cabine", Couleurs.BleuClair, Couleurs.Bleu)
            if (m.emplacement == EMPLACEMENT_PLATEAU) Pastille("🛻 Plateau", Couleurs.OrangeClair, Couleurs.OrangeFonce)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonEmplacement("🚗 Cabine", m.emplacement == EMPLACEMENT_CABINE, Couleurs.Bleu, Modifier.weight(1f)) { onEmplacement(EMPLACEMENT_CABINE) }
            BoutonEmplacement("🛻 Plateau", m.emplacement == EMPLACEMENT_PLATEAU, Couleurs.OrangeFonce, Modifier.weight(1f)) { onEmplacement(EMPLACEMENT_PLATEAU) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonPetit(if (m.recupere) "↩ Annuler" else "🚛 Chargé", Couleurs.Vert, Modifier.weight(1f)) { onCharge() }
            BoutonPetit("🏗️ Chantier", Couleurs.Indigo, Modifier.weight(1f)) { onChantier() }
            BoutonPetit("📦 Livré", Couleurs.Bleu, Modifier.weight(1f)) { onLivre() }
            BoutonPoubelle(onSupprimer)
        }
    }
}

@Composable
private fun BoutonEmplacement(texte: String, actif: Boolean, couleur: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        texte,
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (actif) Color.White else couleur,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (actif) couleur else Color.Transparent)
            .border(1.dp, couleur, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun FicheMateriel(data: AppData, repo: Repository, m: Materiel, onModifier: () -> Unit, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val confirmation = rememberConfirmation()
    val chantiers = Gestion.chantiersDuMateriel(data, m.id)
    FenetrePleine(m.nom, onFermer) {
        Carte {
            Gestion.fournisseur(data, m.fournisseurId)?.let { Ligne("🏭 ${it.nom.trim()}") }
            if (chantiers.isNotEmpty()) Ligne("🏗️ → " + chantiers.joinToString(", ") { Gestion.nomAffiche(it) })
            else Ligne("⚠️ Pas de chantier assigné", Couleurs.OrangeFonce)
            Note(m.notes)
            Pastilles { PastilleCharge(m) }
            BoutonPetit("✏️ Modifier", Couleurs.Indigo) { onModifier() }
        }
        BoutonPlein(if (m.recupere) "↩ Annuler le chargement" else "🚛 Marquer comme chargé", Couleurs.Vert) {
            repo.update { Gestion.basculerCharge(it, m.id, LocalDate.now()) }
            onFermer()
        }
        BoutonPlein("📦 Livré → Archiver", Couleurs.Bleu) {
            confirmation.demander("Marquer comme livré et archiver ?", "Livré") {
                repo.update { Gestion.livrer(it, m.id, LocalDate.now()) }
                onFermer()
            }
        }
        BoutonPlein("🗑️ Supprimer", Couleurs.Rouge) {
            confirmation.demander("Supprimer ce matériel ?") {
                repo.update { Gestion.supprimerMateriel(it, m.id) }
                onFermer()
            }
        }
    }
    ConfirmationHost(confirmation)
}

/** Ajout (existant = null) ou modification d'un matériel du camion. */
@Composable
fun FormulaireMateriel(data: AppData, repo: Repository, existant: Materiel?, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var nom by rememberSaveable { mutableStateOf(existant?.nom ?: "") }
    var fournisseurId by rememberSaveable { mutableStateOf(existant?.fournisseurId ?: "") }
    var chantierId by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf(existant?.notes ?: "") }
    var choixF by rememberSaveable { mutableStateOf(false) }
    var choixC by rememberSaveable { mutableStateOf(false) }

    FenetrePleine(if (existant == null) "Ajouter matériel" else "Modifier le matériel", onFermer) {
        Champ("Nom du matériel *", nom, { nom = it }, "ex: CHAISE")
        SuggestionsProduits(data, nom) { p ->
            nom = p.nom
            if (fournisseurId.isBlank()) fournisseurId = p.fournisseurId
        }
        if (existant == null) {
            val ch = data.chantiers.firstOrNull { it.id == chantierId }
            ChampChoix("Chantier (optionnel)", ch?.let { Gestion.nomAffiche(it) } ?: "-- Aucun chantier --") { choixC = true }
        }
        ChampChoix("Fournisseur (optionnel)", Gestion.fournisseur(data, fournisseurId)?.nom?.trim() ?: "-- Aucun fournisseur --") { choixF = true }
        Champ("Notes (optionnel)", notes, { notes = it }, "ex: fragile, livrer avant 14h…", multiligne = true)
        BoutonPlein("Enregistrer", Couleurs.Vert) {
            if (nom.isBlank()) { Actions.toast(ctx, "Entre le nom du matériel"); return@BoutonPlein }
            if (existant == null) repo.update { Gestion.ajouterMateriel(it, nom, fournisseurId, notes, chantierId).first }
            else repo.update { Gestion.modifierMateriel(it, existant.id, nom.trim(), fournisseurId, notes.trim()) }
            onFermer()
        }
    }
    if (choixF) ChoixFournisseur(data, { fournisseurId = it; choixF = false }) { choixF = false }
    if (choixC) ChoixChantier(data, { chantierId = it; choixC = false }) { choixC = false }
}

@Composable
fun ChoixFournisseur(data: AppData, onChoix: (String) -> Unit, onFermer: () -> Unit) {
    ChoixDialog(
        "Fournisseur",
        Gestion.fournisseursTries(data).map { Option(it.id, it.nom.trim(), it.adresse) },
        libelleAucun = "-- Aucun fournisseur --",
        onChoix = onChoix,
        onFermer = onFermer,
    )
}

@Composable
fun ChoixChantier(data: AppData, onChoix: (String) -> Unit, onFermer: () -> Unit) {
    ChoixDialog(
        "Chantier",
        Gestion.chantiersTries(data).filter { it.statut != STATUT_TERMINE }.map { Option(it.id, Gestion.nomAffiche(it), it.adresse) },
        libelleAucun = "-- Aucun chantier --",
        onChoix = onChoix,
        onFermer = onFermer,
    )
}

@Composable
private fun AffectationChantiers(data: AppData, repo: Repository, m: Materiel, onFermer: () -> Unit) {
    FenetrePleine(m.nom + " → Chantier", onFermer) {
        val chantiers = Gestion.chantiersTries(data).filter { it.statut != STATUT_TERMINE || m.id in it.materiels }
        if (chantiers.isEmpty()) Vide("Aucun chantier créé")
        chantiers.forEach { c ->
            val affecte = m.id in c.materiels
            Carte(lisere = if (affecte) Couleurs.Vert else null, onClick = { repo.update { Gestion.basculerAffectation(it, m.id, c.id) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        TitreCarte(c.nom.trim(), c.surnom.trim())
                        if (c.adresse.isNotBlank()) Ligne("📍 " + c.adresse)
                    }
                    Text(if (affecte) "✅" else "⬜", fontSize = 24.sp)
                }
            }
        }
        BoutonPlein("Terminé") { onFermer() }
    }
}
