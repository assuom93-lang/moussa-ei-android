package com.moussaei.gestion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moussaei.gestion.core.ARRET_FOURNISSEUR
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Palette
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.time.LocalDate

// ---------- Palettes ----------

/** Boutons d'accès à une enseigne : 📍 + ➕ si une seule agence, sinon la liste des agences. */
@Composable
fun BoutonsEnseigne(data: AppData, repo: Repository, enseigne: String, onAgences: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val agences = Gestion.agencesDe(data, enseigne)
    val unique = agences.singleOrNull()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            unique != null && unique.adresse.isNotBlank() -> {
                BoutonPetit("📍 Maps", Couleurs.Bleu, Modifier.weight(1f)) { Actions.ouvrirAdresse(ctx, unique.adresse) }
                BoutonTrajet(data, repo, ARRET_FOURNISSEUR, unique.id, unique.nom.trim(), unique.adresse, Modifier.weight(1f))
            }
            agences.size > 1 -> BoutonPetit("🏬 Voir les ${agences.size} agences (📍 / ➕ Trajet)", Couleurs.Bleu, Modifier.weight(1f)) { onAgences() }
            else -> {}
        }
    }
}

@Composable
fun EcranPalettes(data: AppData, repo: Repository, recherche: String) {
    val confirmation = rememberConfirmation()
    var formulaire by rememberSaveable { mutableStateOf<String?>(null) }
    var agencesDe by rememberSaveable { mutableStateOf<String?>(null) }
    var recup by rememberSaveable { mutableStateOf<String?>(null) } // enseigne pré-choisie ("" = aucune)

    val filtrees = data.palettes.filter { p -> Gestion.correspond(recherche, p.nom, p.note, Gestion.enseigneDePalette(data, p)) }
    // Regroupées par enseigne, par ordre alphabétique (sans enseigne à la fin).
    val groupes = filtrees.groupBy { Gestion.normaliser(Gestion.enseigneDePalette(data, it)) }.values
        .map { g -> Gestion.enseigneDePalette(data, g.first()) to g }
        .sortedWith { a, b ->
            when {
                a.first.isBlank() && b.first.isBlank() -> 0
                a.first.isBlank() -> 1
                b.first.isBlank() -> -1
                else -> Gestion.compareNoms(a.first, b.first)
            }
        }
    val aRendre = Gestion.aRendreParEnseigne(data)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Palettes à rendre
        item {
            Carte(lisere = Couleurs.Orange) {
                val total = Gestion.nbARendre(data)
                TitreCarte("↩ Palettes à rendre" + if (total > 0) " · $total" else "")
                if (total == 0) Ligne("Aucune palette en attente au dépôt.")
                aRendre.forEach { (e, lignes) ->
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                            .background(Couleurs.OrangeClair).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("🏬 $e · ${lignes.sumOf { it.quantite }} palette" + (if (lignes.sumOf { it.quantite } > 1) "s" else ""), fontWeight = FontWeight.Bold, color = Couleurs.OrangeFonce)
                        lignes.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${p.quantite} × " + p.type.ifBlank { "palette" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("récupérée le " + p.dateRecup + (if (p.note.isNotBlank()) " · " + p.note else ""), fontSize = 12.sp, color = Couleurs.Gris)
                                }
                                Text("−", fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { repo.update { Gestion.changerQuantite(it, p.id, -1) } }.padding(horizontal = 10.dp))
                                Text("+", fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { repo.update { Gestion.changerQuantite(it, p.id, 1) } }.padding(horizontal = 10.dp))
                                BoutonPetit("✓ Rendue", Couleurs.Vert) { repo.update { Gestion.marquerRendue(it, p.id, LocalDate.now()) } }
                            }
                        }
                        BoutonsEnseigne(data, repo, e, onAgences = { agencesDe = e })
                    }
                }
                BoutonPlein("➕ J'ai récupéré des palettes", Couleurs.Orange, Modifier.fillMaxWidth().padding(top = 6.dp)) { recup = "" }
                val rendues = data.aRendre.filter { it.rendue }.take(5)
                if (rendues.isNotEmpty()) {
                    Text("Dernières rendues", fontSize = 12.sp, color = Couleurs.Gris, modifier = Modifier.padding(top = 6.dp))
                    rendues.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✅ ${p.quantite} × ${p.type.ifBlank { "palette" }} · ${p.enseigne} · le ${p.dateRendue}", fontSize = 12.sp, color = Couleurs.Gris, modifier = Modifier.weight(1f))
                            Text("annuler", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { repo.update { Gestion.annulerRendue(it, p.id) } }.padding(6.dp))
                            Text("🗑️", fontSize = 13.sp, modifier = Modifier.clickable { repo.update { Gestion.supprimerARendre(it, p.id) } }.padding(6.dp))
                        }
                    }
                }
            }
        }

        item { BoutonPlein("➕ Ajouter un type de palette") { formulaire = "" } }
        if (data.palettes.isEmpty()) item { Vide("Aucune palette enregistrée") }
        else if (groupes.isEmpty()) item { Vide("Aucune palette trouvée pour « $recherche »") }
        items(groupes, key = { "p:" + Gestion.normaliser(it.first) }) { (e, pals) ->
            Carte {
                TitreCarte("🏬 " + e.ifBlank { "Sans enseigne" })
                if (e.isNotBlank()) {
                    val n = Gestion.agencesDe(data, e).size
                    if (n > 1) Ligne("$n agences")
                    else Gestion.agencesDe(data, e).firstOrNull()?.let { LienAdresse(it.adresse) }
                }
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pals.sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { formulaire = p.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("🪵 " + p.nom, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (p.note.isNotBlank()) Text(p.note, fontSize = 12.sp, color = Couleurs.Gris)
                            }
                            BoutonPoubelle { confirmation.demander("Supprimer cette palette ?") { repo.update { Gestion.supprimerPalette(it, p.id) } } }
                        }
                    }
                }
                if (e.isNotBlank()) {
                    BoutonsEnseigne(data, repo, e, onAgences = { agencesDe = e }, modifier = Modifier.padding(top = 4.dp))
                    BoutonContour("↩ J'ai récupéré des palettes $e", Modifier.fillMaxWidth()) { recup = e }
                }
            }
        }
    }

    formulaire?.let { id -> FormulairePalette(data, repo, data.palettes.firstOrNull { it.id == id }) { formulaire = null } }
    agencesDe?.let { e -> AgencesDialog(data, repo, e) { agencesDe = null } }
    recup?.let { e -> FormulaireRecup(data, repo, e) { recup = null } }
    ConfirmationHost(confirmation)
}

@Composable
private fun FormulairePalette(data: AppData, repo: Repository, existante: Palette?, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var nom by rememberSaveable { mutableStateOf(existante?.nom ?: "") }
    var enseigne by rememberSaveable { mutableStateOf(existante?.let { Gestion.enseigneDePalette(data, it) } ?: "") }
    var note by rememberSaveable { mutableStateOf(existante?.note ?: "") }
    var choix by rememberSaveable { mutableStateOf(false) }
    FenetrePleine(if (existante == null) "🪵 Nouveau type de palette" else "🪵 Modifier la palette", onFermer) {
        Champ("Nom de la palette *", nom, { nom = it }, "ex: fabemi, SLM, palette verte…")
        ChampChoix("Enseigne / fournisseur *", enseigne.ifBlank { "-- Choisir --" }) { choix = true }
        Ligne("La palette vaut pour toutes les agences de l'enseigne.")
        Champ("Note (couleur, marquage, détail…)", note, { note = it }, "ex: angle vert ou côté")
        BoutonPlein("Enregistrer", Couleurs.Vert) {
            when {
                nom.isBlank() -> Actions.toast(ctx, "❌ Indique le nom de la palette")
                enseigne.isBlank() -> Actions.toast(ctx, "❌ Choisis l'enseigne")
                else -> {
                    repo.update { Gestion.enregistrerPalette(it, existante?.id ?: "", nom.trim(), enseigne, note.trim()) }
                    onFermer()
                }
            }
        }
    }
    if (choix) ChoixEnseigne(data, { enseigne = it; choix = false }) { choix = false }
}

@Composable
fun ChoixEnseigne(data: AppData, onChoix: (String) -> Unit, onFermer: () -> Unit) {
    ChoixDialog(
        "Enseigne / fournisseur",
        Gestion.enseignes(data).map { e ->
            val n = Gestion.agencesDe(data, e).size
            Option(e, e, if (n > 1) "$n agences" else Gestion.agencesDe(data, e).firstOrNull()?.adresse ?: "")
        },
        onChoix = onChoix,
        onFermer = onFermer,
    )
}

/** « J'ai récupéré des palettes » : enseigne, type, nombre. */
@Composable
private fun FormulaireRecup(data: AppData, repo: Repository, enseigneInitiale: String, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var enseigne by rememberSaveable { mutableStateOf(enseigneInitiale) }
    var type by rememberSaveable { mutableStateOf("") }
    var quantite by rememberSaveable { mutableStateOf(1) }
    var note by rememberSaveable { mutableStateOf("") }
    var choixE by rememberSaveable { mutableStateOf(false) }
    FenetrePleine("↩ Palettes récupérées", onFermer) {
        Ligne("Elles sont gardées au dépôt jusqu'à ce que tu les rendes (dans n'importe quelle agence de l'enseigne).")
        ChampChoix("Enseigne *", enseigne.ifBlank { "-- Choisir --" }) { choixE = true }
        val types = data.palettes.filter { enseigne.isNotBlank() && Gestion.memeEnseigne(Gestion.enseigneDePalette(data, it), enseigne) }
        if (types.isNotEmpty()) {
            Text("Type de palette", fontSize = 12.sp, color = Couleurs.Gris)
            types.sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }.chunked(3).forEach { ligne ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ligne.forEach { p ->
                        BoutonPetit(p.nom, if (type == p.nom) Couleurs.Orange else Couleurs.Gris, Modifier.weight(1f)) { type = if (type == p.nom) "" else p.nom }
                    }
                    repeat(3 - ligne.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Champ("Type (optionnel)", type, { type = it }, "ex: SLM, palette verte…")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nombre", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            BoutonPetit("−", Couleurs.Gris) { if (quantite > 1) quantite-- }
            Text("  $quantite  ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            BoutonPetit("+", Couleurs.Gris) { quantite++ }
        }
        Champ("Note (optionnel)", note, { note = it }, "ex: chantier Voltaire")
        BoutonPlein("✅ Enregistrer", Couleurs.Vert) {
            if (enseigne.isBlank()) { Actions.toast(ctx, "❌ Choisis l'enseigne"); return@BoutonPlein }
            repo.update { Gestion.recupererPalettes(it, enseigne, type, quantite, note, LocalDate.now()) }
            Actions.toast(ctx, "✅ $quantite palette" + (if (quantite > 1) "s" else "") + " $enseigne à rendre")
            onFermer()
        }
    }
    if (choixE) ChoixEnseigne(data, { enseigne = it; type = ""; choixE = false }) { choixE = false }
}

// ---------- Archives ----------

@Composable
fun EcranArchives(data: AppData, repo: Repository, recherche: String) {
    val confirmation = rememberConfirmation()
    val liste = data.archives.filter { Gestion.correspond(recherche, it.nom, it.fournisseurNom, it.chantiersNom, it.dateLivraison) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (liste.isEmpty()) item { Vide(if (recherche.isBlank()) "Aucune livraison archivée" else "Aucun résultat") }
        items(liste, key = { it.id }) { a ->
            Carte {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TitreCarte(a.nom)
                        Ligne("🏭 " + a.fournisseurNom.trim().ifEmpty { "Inconnu" })
                        Ligne("🏗️ → " + a.chantiersNom.trim().ifEmpty { "Aucun" })
                        Pastilles { Pastille("✅ Livré le " + a.dateLivraison, Couleurs.VertClair, Couleurs.VertFonce) }
                    }
                    BoutonPoubelle { confirmation.demander("Supprimer cette archive ?") { repo.update { Gestion.supprimerArchive(it, a.id) } } }
                }
            }
        }
    }
    ConfirmationHost(confirmation)
}
