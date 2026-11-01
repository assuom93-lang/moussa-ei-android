package com.moussaei.gestion.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Ocr
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import java.io.File

@Composable
fun EcranOutils(data: AppData, repo: Repository) {
    val ctx = LocalContext.current
    var liste by rememberSaveable { mutableStateOf("") }
    var chantierId by rememberSaveable { mutableStateOf("") }
    var fournisseurId by rememberSaveable { mutableStateOf("") }
    var choixC by rememberSaveable { mutableStateOf(false) }
    var choixF by rememberSaveable { mutableStateOf(false) }
    var resultatListe by rememberSaveable { mutableStateOf("") }

    var ocrTexte by rememberSaveable { mutableStateOf<String?>(null) }
    var recherche by rememberSaveable { mutableStateOf("") }

    // Photo prise par l'appareil photo, rangée dans le cache de l'appli (jamais dans la galerie).
    val photoUri: Uri = remember {
        val dossier = File(ctx.cacheDir, "photos").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", File(dossier, "photo.jpg"))
    }

    fun analyser(uri: Uri) {
        ocrTexte = "⏳ Analyse en cours…"
        try {
            val image = InputImage.fromFilePath(ctx, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { r -> ocrTexte = Ocr.nettoyer(r.text).ifBlank { "(aucun texte détecté)" } }
                .addOnFailureListener { e -> ocrTexte = "❌ Erreur : " + (e.message ?: "lecture impossible") }
        } catch (e: Exception) {
            ocrTexte = "❌ Erreur : " + (e.message ?: "image illisible")
        }
    }

    val photoOcr = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) analyser(photoUri) }
    val galerieOcr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) analyser(uri) }
    val photoLens = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) Actions.photoVersLens(ctx, photoUri) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Carte {
            TitreCarte("📋 Liste matériel rapide")
            Ligne("Écris ta liste de matériel (1 par ligne), tout sera ajouté au camion d'un coup")
            Champ("Liste", liste, { liste = it }, "Ba 13 x10\nSac ciment x5\nRubalise\nPlaco hydro", multiligne = true)
            val ch = data.chantiers.firstOrNull { it.id == chantierId }
            ChampChoix("Chantier (optionnel)", ch?.let { Gestion.nomAffiche(it) } ?: "-- Chantier --") { choixC = true }
            ChampChoix("Fournisseur (optionnel)", Gestion.fournisseur(data, fournisseurId)?.nom?.trim() ?: "-- Fournisseur --") { choixF = true }
            BoutonPlein("📋 Ajouter toute la liste au camion", Couleurs.Orange) {
                if (liste.isBlank()) { Actions.toast(ctx, "Écris au moins un matériel !"); return@BoutonPlein }
                var n = 0
                repo.update { d -> Gestion.ajouterListe(d, liste, fournisseurId, chantierId).also { n = it.second }.first }
                resultatListe = "✅ $n matériel" + (if (n > 1) "s ajoutés" else " ajouté") + (ch?.let { " → " + it.nom.trim() } ?: "")
                liste = ""
            }
            if (resultatListe.isNotEmpty()) {
                Text(
                    resultatListe,
                    color = Couleurs.VertFonce,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Couleurs.VertClair).padding(10.dp),
                )
            }
        }

        Carte {
            TitreCarte("📝 Scanner du texte")
            Ligne("Prends une photo d'une étiquette, d'un bon de livraison ou d'une référence pour en extraire le texte. L'analyse se fait sur le téléphone, même sans connexion.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPetit("📷 Prendre une photo", Couleurs.Vert, Modifier.weight(1f)) { photoOcr.launch(photoUri) }
                BoutonPetit("🖼️ Choisir une image", Couleurs.Indigo, Modifier.weight(1f)) { galerieOcr.launch("image/*") }
            }
            ocrTexte?.let { t ->
                Champ("Texte scanné", t, { ocrTexte = it }, multiligne = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonPetit("📋 Copier", Couleurs.Bleu, Modifier.weight(1f)) { Actions.copier(ctx, t, "Texte copié !") }
                    BoutonPetit("📋 → Liste matériel", Couleurs.Orange, Modifier.weight(1f)) {
                        liste = if (liste.isBlank()) t else liste.trimEnd() + "\n" + t
                        Actions.toast(ctx, "Texte ajouté à la liste rapide")
                    }
                }
            }
        }

        Carte {
            TitreCarte("📸 Recherche visuelle")
            Ligne("Trouve un article à partir d'une photo avec Google Lens")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPetit("📷 Photo → Lens", Couleurs.Vert, Modifier.weight(1f)) { photoLens.launch(photoUri) }
                BoutonPetit("🔎 Ouvrir Lens", Couleurs.Bleu, Modifier.weight(1f)) { Actions.ouvrirLens(ctx) }
            }
        }

        Carte {
            TitreCarte("🔍 Rechercher un article")
            Ligne("Tape un nom de produit pour le trouver sur Google")
            Champ("Produit", recherche, { recherche = it }, "ex: Rubalise, Marteau piqueur…")
            BoutonPlein("🔍 Rechercher sur Google") {
                if (recherche.isBlank()) Actions.toast(ctx, "Tape un nom de produit") else Actions.rechercherGoogle(ctx, recherche)
            }
        }
        Text(" ", fontSize = 4.sp)
    }

    if (choixC) ChoixChantier(data, { chantierId = it; choixC = false }) { choixC = false }
    if (choixF) ChoixFournisseur(data, { fournisseurId = it; choixF = false }) { choixF = false }
}
