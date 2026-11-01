package com.moussaei.gestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Chantier
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.StatutEffectif
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.time.LocalDate

@Composable
fun EcranChantiers(data: AppData, repo: Repository, recherche: String) {
    var ficheId by rememberSaveable { mutableStateOf<String?>(null) }
    var formulaire by rememberSaveable { mutableStateOf<String?>(null) } // "" = nouveau, id = modification

    val liste = Gestion.chantiersTries(data).filter {
        Gestion.correspond(recherche, it.nom, it.surnom, it.adresse, it.chef, it.tel, it.notes)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BoutonPlein("➕ Ajouter un chantier") { formulaire = "" } }
        if (liste.isEmpty()) item { Vide(if (recherche.isBlank()) "Aucun chantier" else "Aucun résultat") }
        items(liste, key = { it.id }) { c -> CarteChantier(data, c) { ficheId = c.id } }
    }

    ficheId?.let { id ->
        val c = data.chantiers.firstOrNull { it.id == id }
        if (c != null) FicheChantier(data, repo, c, onModifier = { formulaire = c.id }, onFermer = { ficheId = null })
    }

    formulaire?.let { id ->
        FormulaireChantier(
            existant = data.chantiers.firstOrNull { it.id == id },
            onEnregistrer = { c ->
                if (id.isEmpty()) repo.update { Gestion.ajouterChantier(it, c) }
                else repo.update { Gestion.modifierChantier(it, id, c.nom, c.surnom, c.adresse, c.chef, c.tel, c.notes) }
                formulaire = null
            },
            onFermer = { formulaire = null },
        )
    }
}

@Composable
fun PastilleStatut(s: StatutEffectif) = when (s) {
    StatutEffectif.EN_LIVRAISON -> Pastille("🚚 En livraison", Couleurs.OrangeClair, Couleurs.OrangeFonce)
    StatutEffectif.EN_COURS -> Pastille("🔄 En cours", Couleurs.BleuClair, Couleurs.Bleu)
    StatutEffectif.TERMINE -> Pastille("✅ Terminé", Couleurs.VertClair, Couleurs.VertFonce)
}

@Composable
private fun CarteChantier(data: AppData, c: Chantier, onClick: () -> Unit) {
    val s = Gestion.statut(data, c)
    val nb = Gestion.materielsValides(data, c).size
    Carte(
        lisere = if (s == StatutEffectif.EN_LIVRAISON) Couleurs.Orange else null,
        attenuee = s == StatutEffectif.TERMINE,
        onClick = onClick,
    ) {
        TitreCarte(c.nom.trim(), c.surnom.trim())
        LienAdresse(c.adresse)
        if (c.chef.isNotBlank()) Ligne("👤 ${c.chef.trim()}")
        LienTel(c.tel)
        Note(c.notes)
        Pastilles {
            Pastille("$nb matériel" + (if (nb > 1) "s" else ""), Couleurs.BleuClair, Couleurs.Bleu)
            PastilleStatut(s)
        }
    }
}

@Composable
private fun FicheChantier(data: AppData, repo: Repository, c: Chantier, onModifier: () -> Unit, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val confirmation = rememberConfirmation()
    var ajout by rememberSaveable { mutableStateOf(false) }
    val s = Gestion.statut(data, c)

    FenetrePleine(Gestion.nomAffiche(c), onFermer) {
        Carte {
            if (c.surnom.isNotBlank()) Ligne("📌 ${c.surnom.trim()}")
            LienAdresse(c.adresse, avecCopie = true)
            if (c.chef.isNotBlank()) Ligne("👤 ${c.chef.trim()}")
            LienTel(c.tel)
            Note(c.notes)
            Pastilles { PastilleStatut(s) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPetit("✏️ Modifier", Couleurs.Indigo, Modifier.weight(1f)) { onModifier() }
                if (s == StatutEffectif.TERMINE) {
                    BoutonPetit("🔄 Réouvrir", Couleurs.Orange, Modifier.weight(1f)) { repo.update { Gestion.basculerStatut(it, c.id) } }
                } else {
                    BoutonPetit("✅ Terminer", Couleurs.Vert, Modifier.weight(1f)) {
                        repo.update { Gestion.basculerStatut(it, c.id) }
                        onFermer()
                    }
                }
            }
        }

        SectionTitre("Matériels à livrer")
        BoutonPlein("➕ Ajouter matériel") { ajout = true }
        val mats = Gestion.materielsValides(data, c)
        if (mats.isEmpty()) Vide("Aucun matériel pour ce chantier")
        mats.forEach { m ->
            Carte(lisere = if (m.recupere) Couleurs.Vert else null) {
                TitreCarte(m.nom)
                Gestion.fournisseur(data, m.fournisseurId)?.let { Ligne("🏭 ${it.nom.trim()}") }
                Pastilles {
                    if (m.recupere) Pastille("🚛 Chargé", Couleurs.VertClair, Couleurs.VertFonce)
                    else Pastille("✕ À charger", Couleurs.RougeClair, Couleurs.RougeFonce)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BoutonPetit("✓ Livré", Couleurs.Vert, Modifier.weight(1f)) {
                        confirmation.demander("Marquer comme livré et archiver ?", "Livré") {
                            repo.update { Gestion.livrer(it, m.id, LocalDate.now(), depuisChantierId = c.id) }
                        }
                    }
                    BoutonPetit("✕ Retirer", Couleurs.Rouge, Modifier.weight(1f)) {
                        confirmation.demander("Retirer ce matériel du chantier ?", "Retirer") {
                            repo.update { Gestion.retirerDuChantier(it, c.id, m.id) }
                        }
                    }
                }
            }
        }

        BoutonPlein("🗑️ Supprimer ce chantier", Couleurs.Rouge) {
            confirmation.demander("Supprimer ce chantier ?") {
                repo.update { Gestion.supprimerChantier(it, c.id) }
                onFermer()
            }
        }
    }

    if (ajout) {
        AjoutMaterielChantier(data, onAjouter = { matId, nom ->
            var res = Gestion.AjoutResultat.INVALIDE
            repo.update { d -> Gestion.ajouterAuChantier(d, c.id, matId, nom).also { res = it.second }.first }
            when (res) {
                Gestion.AjoutResultat.AJOUTE -> { Actions.toast(ctx, "✓ Matériel ajouté au chantier"); ajout = false }
                Gestion.AjoutResultat.DEJA_PRESENT -> Actions.toast(ctx, "Ce matériel est déjà dans ce chantier")
                Gestion.AjoutResultat.INVALIDE -> Actions.toast(ctx, "Choisis un matériel ou écris un nom")
            }
        }, onFermer = { ajout = false })
    }
    ConfirmationHost(confirmation)
}

@Composable
private fun AjoutMaterielChantier(data: AppData, onAjouter: (String, String) -> Unit, onFermer: () -> Unit) {
    var matId by rememberSaveable { mutableStateOf("") }
    var nom by rememberSaveable { mutableStateOf("") }
    var choix by rememberSaveable { mutableStateOf(false) }
    FenetrePleine("Ajouter matériel au chantier", onFermer) {
        val sel = data.materiels.firstOrNull { it.id == matId }
        ChampChoix("Matériel du camion", sel?.nom ?: "-- Sélectionner un matériel --") { choix = true }
        Text("Ou créer un nouveau", modifier = Modifier.fillMaxWidth())
        Champ("Nom du nouveau matériel", nom, { nom = it; if (it.isNotBlank()) matId = "" }, placeholder = "ex: TABLE")
        BoutonPlein("Ajouter", Couleurs.Vert) { onAjouter(matId, nom) }
    }
    if (choix) {
        ChoixDialog(
            "Matériel du camion",
            data.materiels.map { m -> Option(m.id, m.nom, Gestion.fournisseur(data, m.fournisseurId)?.nom ?: "") },
            libelleAucun = "-- Aucun --",
            onChoix = { matId = it; if (it.isNotEmpty()) nom = ""; choix = false },
            onFermer = { choix = false },
        )
    }
}

@Composable
private fun FormulaireChantier(existant: Chantier?, onEnregistrer: (Chantier) -> Unit, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var nom by rememberSaveable { mutableStateOf(existant?.nom?.trim() ?: "") }
    var surnom by rememberSaveable { mutableStateOf(existant?.surnom?.trim() ?: "") }
    var adresse by rememberSaveable { mutableStateOf(existant?.adresse ?: "") }
    var chef by rememberSaveable { mutableStateOf(existant?.chef?.trim() ?: "") }
    var tel by rememberSaveable { mutableStateOf(existant?.tel ?: "") }
    var notes by rememberSaveable { mutableStateOf(existant?.notes ?: "") }

    FenetrePleine(if (existant == null) "Ajouter un chantier" else "Modifier le chantier", onFermer) {
        Champ("Nom du chantier *", nom, { nom = it }, "ex: Saint-Maur")
        Champ("Surnom (optionnel)", surnom, { surnom = it }, "ex: Stade de France")
        Champ("Adresse *", adresse, { adresse = it }, "ex: Boulevard de la Marne")
        Champ("Chef de chantier", chef, { chef = it }, "ex: Jean Dupont")
        Champ("Téléphone", tel, { tel = it }, "ex: 06 XX XX XX XX", clavier = KeyboardType.Phone)
        Champ("Notes (optionnel)", notes, { notes = it }, "ex: code portail 1234, livrer avant 14h…", multiligne = true)
        BoutonPlein("Enregistrer", Couleurs.Vert) {
            if (nom.isBlank() || adresse.isBlank()) {
                Actions.toast(ctx, "Le nom et l'adresse sont obligatoires")
            } else {
                onEnregistrer(
                    Chantier(
                        id = existant?.id ?: "",
                        nom = nom.trim(), surnom = surnom.trim(), adresse = adresse.trim(),
                        chef = chef.trim(), tel = tel.trim(), notes = notes.trim(),
                    )
                )
            }
        }
    }
}
