package com.moussaei.gestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moussaei.gestion.core.ARRET_FOURNISSEUR
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Fournisseur
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.time.LocalDate

val CouleurPalette = Color(0xFF6B4F2A)
val FondPalette = Color(0xFFEDE4D8)

/**
 * Fournisseurs regroupés par enseigne : une enseigne à plusieurs agences (Point P, Raboni…)
 * est une seule carte qui ouvre la liste de ses agences ; les autres restent à part.
 * Avec une recherche, la liste devient plate (toutes les agences qui correspondent).
 */
@Composable
fun EcranFournisseurs(data: AppData, repo: Repository, recherche: String) {
    var ficheId by rememberSaveable { mutableStateOf<String?>(null) }
    var formulaire by rememberSaveable { mutableStateOf<String?>(null) }
    var enseigneOuverte by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BoutonPlein("➕ Ajouter fournisseur") { formulaire = "" } }
        if (recherche.isNotBlank()) {
            val liste = Gestion.fournisseursTries(data).filter { Gestion.correspond(recherche, it.nom, it.adresse, it.enseigne) }
            if (liste.isEmpty()) item { Vide("Aucun résultat") }
            items(liste, key = { it.id }) { f -> CarteFournisseur(data, repo, f) { ficheId = f.id } }
        } else {
            val groupes = Gestion.enseignes(data).map { e -> e to Gestion.agencesDe(data, e) }
            if (groupes.isEmpty()) item { Vide("Aucun fournisseur") }
            items(groupes, key = { "e:" + Gestion.normaliser(it.first) }) { (e, agences) ->
                if (agences.size == 1) {
                    CarteFournisseur(data, repo, agences[0]) { ficheId = agences[0].id }
                } else {
                    val nbPal = data.palettes.count { Gestion.memeEnseigne(Gestion.enseigneDePalette(data, it), e) }
                    val nbRendre = Gestion.aRendreParEnseigne(data).firstOrNull { Gestion.memeEnseigne(it.first, e) }?.second?.sumOf { it.quantite } ?: 0
                    Carte(lisere = Couleurs.Bleu, onClick = { enseigneOuverte = e }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                TitreCarte("🏬 $e")
                                Ligne("${agences.size} agences")
                            }
                            Text("›", fontSize = 24.sp, color = Couleurs.Gris)
                        }
                        if (nbPal > 0 || nbRendre > 0) Pastilles {
                            if (nbPal > 0) Pastille("🪵 $nbPal palette" + (if (nbPal > 1) "s" else ""), FondPalette, CouleurPalette)
                            if (nbRendre > 0) Pastille("↩ $nbRendre à rendre", Couleurs.OrangeClair, Couleurs.OrangeFonce)
                        }
                    }
                }
            }
        }
    }

    enseigneOuverte?.let { e ->
        AgencesDialog(data, repo, e, onOuvrirFiche = { ficheId = it }) { enseigneOuverte = null }
    }
    ficheId?.let { id ->
        data.fournisseurs.firstOrNull { it.id == id }?.let { f ->
            FicheFournisseur(data, repo, f, onModifier = { formulaire = f.id }, onFermer = { ficheId = null })
        }
    }
    formulaire?.let { id -> FormulaireFournisseur(data, repo, data.fournisseurs.firstOrNull { it.id == id }) { formulaire = null } }
}

@Composable
private fun CarteFournisseur(data: AppData, repo: Repository, f: Fournisseur, onClick: () -> Unit) {
    val mats = data.materiels.filter { it.fournisseurId == f.id }
    val nbCharges = mats.count { it.recupere }
    val nbPal = data.palettes.count { Gestion.memeEnseigne(Gestion.enseigneDePalette(data, it), Gestion.enseigneDe(f)) }
    Carte(onClick = onClick) {
        TitreCarte(f.nom.trim())
        if (f.enseigne.isNotBlank() && !Gestion.memeEnseigne(f.enseigne, f.nom)) Ligne("🏬 " + f.enseigne)
        LienAdresse(f.adresse)
        if (mats.isNotEmpty() || nbPal > 0) Pastilles {
            if (mats.isNotEmpty()) Pastille("${mats.size} matériel" + (if (mats.size > 1) "s" else ""), Couleurs.BleuClair, Couleurs.Bleu)
            if (nbCharges > 0) Pastille("$nbCharges chargé" + (if (nbCharges > 1) "s" else ""), Couleurs.VertClair, Couleurs.VertFonce)
            if (nbPal > 0) Pastille("🪵 $nbPal palette" + (if (nbPal > 1) "s" else ""), FondPalette, CouleurPalette)
        }
        if (f.adresse.isNotBlank()) {
            BoutonTrajet(data, repo, ARRET_FOURNISSEUR, f.id, f.nom.trim(), f.adresse, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

/** Toutes les agences d'une enseigne, avec recherche, 📍 Maps et ➕ Trajet. */
@Composable
fun AgencesDialog(data: AppData, repo: Repository, enseigne: String, onOuvrirFiche: ((String) -> Unit)? = null, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val agences = Gestion.agencesDe(data, enseigne)
    val palettes = data.palettes.filter { Gestion.memeEnseigne(Gestion.enseigneDePalette(data, it), enseigne) }
    FenetrePleine("🏬 $enseigne", onFermer) {
        if (palettes.isNotEmpty()) {
            Carte {
                Text("🪵 Palettes $enseigne", fontWeight = FontWeight.Bold)
                Text(palettes.sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }.joinToString(" · ") { it.nom }, fontSize = 13.sp)
            }
        }
        if (agences.size + Gestion.lexique(data, repo.catalogue, enseigne).size > 5) {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("🔍 Ville, code postal…") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (agences.isNotEmpty()) SectionTitre("Mes fournisseurs $enseigne")
        agences.filter { Gestion.correspond(q, it.nom, it.adresse) }.forEach { f ->
            Carte(onClick = onOuvrirFiche?.let { o -> { o(f.id) } }) {
                TitreCarte(f.nom.trim())
                LienAdresse(f.adresse)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonPetit("📍 Maps", Couleurs.Bleu, Modifier.weight(1f)) { Actions.ouvrirAdresse(ctx, f.adresse) }
                    BoutonTrajet(data, repo, ARRET_FOURNISSEUR, f.id, f.nom.trim(), f.adresse, Modifier.weight(1f))
                }
            }
        }
        val autres = Gestion.lexique(data, repo.catalogue, enseigne).filter { Gestion.correspond(q, it.nom, it.adresse) }
        if (autres.isNotEmpty()) {
            SectionTitre("📖 Autres agences $enseigne (lexique) · ${autres.size}")
            autres.forEach { a -> CarteAgenceLexique(data, repo, a) }
        }
    }
}

@Composable
private fun FicheFournisseur(data: AppData, repo: Repository, f: Fournisseur, onModifier: () -> Unit, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val confirmation = rememberConfirmation()
    var voirAgences by rememberSaveable { mutableStateOf(false) }
    val enseigne = Gestion.enseigneDe(f)
    val nbAgences = Gestion.agencesDe(data, enseigne).size
    val mats = data.materiels.filter { it.fournisseurId == f.id }
    val palettes = data.palettes.filter { Gestion.memeEnseigne(Gestion.enseigneDePalette(data, it), enseigne) }
    FenetrePleine(f.nom.trim(), onFermer) {
        Carte {
            if (f.enseigne.isNotBlank()) Ligne("🏬 Enseigne : " + f.enseigne)
            LienAdresse(f.adresse, avecCopie = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPetit("📍 Maps", Couleurs.Bleu, Modifier.weight(1f)) { Actions.ouvrirAdresse(ctx, f.adresse) }
                BoutonTrajet(data, repo, ARRET_FOURNISSEUR, f.id, f.nom.trim(), f.adresse, Modifier.weight(1f))
            }
            BoutonPetit("✏️ Modifier", Couleurs.Indigo) { onModifier() }
        }
        val nbLexique = Gestion.lexique(data, repo.catalogue, enseigne).size
        if (nbAgences > 1 || nbLexique > 0) BoutonContour("🏬 Toutes les agences $enseigne" + if (nbLexique > 0) " (+ $nbLexique dans le lexique)" else "") { voirAgences = true }
        if (palettes.isNotEmpty()) {
            SectionTitre("Palettes $enseigne (${palettes.size})")
            Carte {
                palettes.sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }.forEach { p ->
                    Ligne("🪵 " + p.nom + if (p.note.isNotBlank()) " · " + p.note else "", Color.Unspecified)
                }
            }
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
        BoutonPlein("🗑️ Supprimer ce fournisseur", Couleurs.Rouge) {
            confirmation.demander("Supprimer ce fournisseur ?") {
                repo.update { Gestion.supprimerFournisseur(it, f.id) }
                onFermer()
            }
        }
    }
    if (voirAgences) AgencesDialog(data, repo, enseigne) { voirAgences = false }
    ConfirmationHost(confirmation)
}

@Composable
private fun FormulaireFournisseur(data: AppData, repo: Repository, existant: Fournisseur?, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var nom by rememberSaveable { mutableStateOf(existant?.nom?.trim() ?: "") }
    var adresse by rememberSaveable { mutableStateOf(existant?.adresse ?: "") }
    var enseigne by rememberSaveable { mutableStateOf(existant?.enseigne ?: "") }
    var choix by rememberSaveable { mutableStateOf(false) }
    FenetrePleine(if (existant == null) "Ajouter fournisseur" else "Modifier le fournisseur", onFermer) {
        Champ("Nom du fournisseur *", nom, {
            nom = it
            if (existant == null && enseigne.isBlank()) Gestion.devinerEnseigne(it).let { e -> if (e.isNotEmpty()) enseigne = e }
        }, "ex: Point P Bobigny")
        Champ("Adresse", adresse, { adresse = it }, "ex: 44 rue Andreï Sakharov, 93140 Bondy")
        Champ("Enseigne (optionnel)", enseigne, { enseigne = it }, "ex: Point P, Raboni… (vide si fournisseur unique)")
        BoutonContour("🏬 Choisir une enseigne existante") { choix = true }
        BoutonPlein("Enregistrer", Couleurs.Vert) {
            if (nom.isBlank()) { Actions.toast(ctx, "Le nom est obligatoire"); return@BoutonPlein }
            if (existant == null) repo.update { Gestion.ajouterFournisseur(it, nom.trim(), adresse.trim(), enseigne) }
            else repo.update { Gestion.modifierFournisseur(it, existant.id, nom.trim(), adresse.trim(), enseigne) }
            onFermer()
        }
    }
    if (choix) ChoixDialog(
        "Enseigne",
        Gestion.enseignes(data).filter { e -> Gestion.agencesDe(data, e).size > 1 || data.fournisseurs.any { it.enseigne.isNotBlank() && Gestion.memeEnseigne(it.enseigne, e) } }
            .map { Option(it, it, "${Gestion.agencesDe(data, it).size} agence(s)") },
        libelleAucun = "-- Aucune enseigne --",
        onChoix = { enseigne = it; choix = false },
        onFermer = { choix = false },
    )
}
