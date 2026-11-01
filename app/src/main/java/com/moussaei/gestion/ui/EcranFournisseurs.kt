package com.moussaei.gestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Fournisseur
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.time.LocalDate

@Composable
fun EcranFournisseurs(data: AppData, repo: Repository, recherche: String) {
    var ficheId by rememberSaveable { mutableStateOf<String?>(null) }
    var formulaire by rememberSaveable { mutableStateOf<String?>(null) }

    val liste = Gestion.fournisseursTries(data).filter { Gestion.correspond(recherche, it.nom, it.adresse) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BoutonPlein("➕ Ajouter fournisseur") { formulaire = "" } }
        if (liste.isEmpty()) item { Vide(if (recherche.isBlank()) "Aucun fournisseur" else "Aucun résultat") }
        items(liste, key = { it.id }) { f ->
            val mats = data.materiels.filter { it.fournisseurId == f.id }
            val nbCharges = mats.count { it.recupere }
            Carte(onClick = { ficheId = f.id }) {
                TitreCarte(f.nom.trim())
                LienAdresse(f.adresse)
                if (mats.isNotEmpty()) Pastilles {
                    Pastille("${mats.size} matériel" + (if (mats.size > 1) "s" else ""), Couleurs.BleuClair, Couleurs.Bleu)
                    if (nbCharges > 0) Pastille("$nbCharges chargé" + (if (nbCharges > 1) "s" else ""), Couleurs.VertClair, Couleurs.VertFonce)
                }
            }
        }
    }

    ficheId?.let { id ->
        data.fournisseurs.firstOrNull { it.id == id }?.let { f ->
            FicheFournisseur(data, repo, f, onModifier = { formulaire = f.id }, onFermer = { ficheId = null })
        }
    }
    formulaire?.let { id -> FormulaireFournisseur(repo, data.fournisseurs.firstOrNull { it.id == id }) { formulaire = null } }
}

@Composable
private fun FicheFournisseur(data: AppData, repo: Repository, f: Fournisseur, onModifier: () -> Unit, onFermer: () -> Unit) {
    val confirmation = rememberConfirmation()
    val mats = data.materiels.filter { it.fournisseurId == f.id }
    val palettes = data.palettes.filter { it.fournisseurId == f.id }
    FenetrePleine(f.nom.trim(), onFermer) {
        Carte {
            LienAdresse(f.adresse, avecCopie = true)
            BoutonPetit("✏️ Modifier", Couleurs.Indigo) { onModifier() }
        }
        SectionTitre("Matériels (${mats.size})")
        if (mats.isEmpty()) Vide("Aucun matériel chez ce fournisseur")
        mats.forEach { m ->
            val ch = Gestion.chantiersDuMateriel(data, m.id)
            Carte(lisere = if (m.recupere) Couleurs.Vert else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TitreCarte(m.nom)
                        if (ch.isNotEmpty()) Ligne("🏗️ → " + ch.joinToString(", ") { Gestion.nomAffiche(it) })
                        Pastilles { PastilleCharge(m) }
                    }
                    BoutonPetit(if (m.recupere) "↩ Annuler" else "🚛 Chargé", Couleurs.Vert) {
                        repo.update { Gestion.basculerCharge(it, m.id, LocalDate.now()) }
                    }
                }
            }
        }
        if (palettes.isNotEmpty()) {
            SectionTitre("Palettes (${palettes.size})")
            Carte {
                palettes.sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }.forEach { p ->
                    Ligne("🪵 " + p.nom + if (p.note.isNotBlank()) " · " + p.note else "", androidx.compose.ui.graphics.Color.Unspecified)
                }
            }
        }
        BoutonPlein("🗑️ Supprimer ce fournisseur", Couleurs.Rouge) {
            confirmation.demander("Supprimer ce fournisseur ?") {
                repo.update { Gestion.supprimerFournisseur(it, f.id) }
                onFermer()
            }
        }
    }
    ConfirmationHost(confirmation)
}

@Composable
private fun FormulaireFournisseur(repo: Repository, existant: Fournisseur?, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var nom by rememberSaveable { mutableStateOf(existant?.nom?.trim() ?: "") }
    var adresse by rememberSaveable { mutableStateOf(existant?.adresse ?: "") }
    FenetrePleine(if (existant == null) "Ajouter fournisseur" else "Modifier le fournisseur", onFermer) {
        Champ("Nom du fournisseur *", nom, { nom = it }, "ex: ACIPAR")
        Champ("Adresse", adresse, { adresse = it }, "ex: 44 RUE ANDREI SAKHAROV, 93140 BONDY")
        BoutonPlein("Enregistrer", Couleurs.Vert) {
            if (nom.isBlank()) { Actions.toast(ctx, "Le nom est obligatoire"); return@BoutonPlein }
            if (existant == null) repo.update { Gestion.ajouterFournisseur(it, nom.trim(), adresse.trim()) }
            else repo.update { Gestion.modifierFournisseur(it, existant.id, nom.trim(), adresse.trim()) }
            onFermer()
        }
    }
}
