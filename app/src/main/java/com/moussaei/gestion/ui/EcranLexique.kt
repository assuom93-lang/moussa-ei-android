package com.moussaei.gestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moussaei.gestion.core.ARRET_LIBRE
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions

/** Une agence du lexique : 📍 Maps, ➕ Trajet, ➕ Mes fournisseurs. */
@Composable
fun CarteAgenceLexique(data: AppData, repo: Repository, a: Gestion.AgenceCatalogue) {
    val ctx = LocalContext.current
    Carte {
        TitreCarte(a.nom)
        LienAdresse(a.adresse)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonPetit("📍 Maps", Couleurs.Bleu, Modifier.weight(1f)) { Actions.ouvrirAdresse(ctx, a.adresse) }
            BoutonTrajet(data, repo, ARRET_LIBRE, Gestion.idAgence(a), a.nom, a.adresse, Modifier.weight(1f))
        }
        BoutonContour("➕ Ajouter à mes fournisseurs", Modifier.fillMaxWidth()) {
            repo.update { Gestion.ajouterAuxFournisseurs(it, a) }
            Actions.toast(ctx, "✅ ${a.nom} ajouté à tes fournisseurs")
        }
    }
}

/** Lexique : agences Île-de-France des enseignes de tes fournisseurs, en consultation. */
@Composable
fun EcranLexique(data: AppData, repo: Repository, recherche: String) {
    var filtre by rememberSaveable { mutableStateOf("") }
    val tout = Gestion.lexique(data, repo.catalogue)
    val enseignes = tout.map { it.enseigne }.distinct().sortedWith { a, b -> Gestion.compareNoms(a, b) }
    val liste = tout.filter { (filtre.isBlank() || Gestion.memeEnseigne(it.enseigne, filtre)) && Gestion.correspond(recherche, it.nom, it.adresse) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Ligne("Agences d'Île-de-France des enseignes de tes fournisseurs. Elles ne sont pas dans « Mes fournisseurs » : ajoute seulement celles que tu utilises.")
        }
        if (enseignes.size > 1) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoutonPetit("Toutes", if (filtre.isBlank()) Couleurs.Bleu else Couleurs.Gris, Modifier.weight(1f)) { filtre = "" }
                enseignes.forEach { e ->
                    BoutonPetit(e, if (Gestion.memeEnseigne(filtre, e)) Couleurs.Bleu else Couleurs.Gris, Modifier.weight(1f)) { filtre = e }
                }
            }
        }
        item { Ligne("${liste.size} agence" + (if (liste.size > 1) "s" else "")) }
        if (liste.isEmpty()) item { Vide(if (tout.isEmpty()) "Aucune agence pour tes enseignes" else "Aucun résultat") }
        items(liste, key = { Gestion.idAgence(it) }) { a -> CarteAgenceLexique(data, repo, a) }
    }
}
