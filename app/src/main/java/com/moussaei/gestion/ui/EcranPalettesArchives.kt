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
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Palette
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions

// ---------- Palettes ----------

@Composable
fun EcranPalettes(data: AppData, repo: Repository, recherche: String) {
    val confirmation = rememberConfirmation()
    var formulaire by rememberSaveable { mutableStateOf<String?>(null) }

    val filtrees = data.palettes.filter { p ->
        Gestion.correspond(recherche, p.nom, p.note, Gestion.fournisseur(data, p.fournisseurId)?.nom)
    }
    // Regroupées par fournisseur, fournisseurs par ordre alphabétique (inconnu à la fin).
    val groupes = filtrees.groupBy { it.fournisseurId }.entries.sortedWith { a, b ->
        val fa = Gestion.fournisseur(data, a.key)?.nom
        val fb = Gestion.fournisseur(data, b.key)?.nom
        when {
            fa == null && fb == null -> 0
            fa == null -> 1
            fb == null -> -1
            else -> Gestion.compareNoms(fa, fb)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BoutonPlein("➕ Ajouter une palette") { formulaire = "" } }
        if (data.palettes.isEmpty()) item { Vide("Aucune palette enregistrée") }
        else if (groupes.isEmpty()) item { Vide("Aucune palette trouvée pour « $recherche »") }
        items(groupes, key = { it.key.ifEmpty { "_aucun" } }) { (fid, pals) ->
            val f = Gestion.fournisseur(data, fid)
            Carte {
                TitreCarte("🏭 " + (f?.nom?.trim() ?: "Fournisseur inconnu"))
                if (f != null) LienAdresse(f.adresse)
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
            }
        }
    }

    formulaire?.let { id -> FormulairePalette(data, repo, data.palettes.firstOrNull { it.id == id }) { formulaire = null } }
    ConfirmationHost(confirmation)
}

@Composable
private fun FormulairePalette(data: AppData, repo: Repository, existante: Palette?, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var nom by rememberSaveable { mutableStateOf(existante?.nom ?: "") }
    var fid by rememberSaveable { mutableStateOf(existante?.fournisseurId ?: "") }
    var note by rememberSaveable { mutableStateOf(existante?.note ?: "") }
    var choix by rememberSaveable { mutableStateOf(false) }
    FenetrePleine(if (existante == null) "🪵 Nouvelle palette" else "🪵 Modifier la palette", onFermer) {
        Champ("Nom de la palette *", nom, { nom = it }, "ex: fabemi, SLM, palette verte…")
        ChampChoix("Fournisseur *", Gestion.fournisseur(data, fid)?.nom?.trim() ?: "-- Choisir le fournisseur --") { choix = true }
        Champ("Note (couleur, marquage, détail…)", note, { note = it }, "ex: angle vert ou côté")
        BoutonPlein("Enregistrer", Couleurs.Vert) {
            when {
                nom.isBlank() -> Actions.toast(ctx, "❌ Indique le nom de la palette")
                fid.isBlank() -> Actions.toast(ctx, "❌ Choisis le fournisseur")
                else -> {
                    repo.update { Gestion.enregistrerPalette(it, existante?.id ?: "", nom.trim(), fid, note.trim()) }
                    onFermer()
                }
            }
        }
    }
    if (choix) ChoixDialog(
        "Fournisseur",
        Gestion.fournisseursTries(data).map { Option(it.id, it.nom.trim(), it.adresse) },
        onChoix = { fid = it; choix = false },
        onFermer = { choix = false },
    )
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
