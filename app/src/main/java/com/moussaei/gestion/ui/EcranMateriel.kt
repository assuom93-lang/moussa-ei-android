package com.moussaei.gestion.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Produit
import com.moussaei.gestion.core.Produits
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import com.moussaei.gestion.util.Photos
import java.io.File

// ---------- Photos ----------

/** Icône 🏞 : appuyer affiche la photo en grand. Rien si pas de photo. */
@Composable
fun IconePhoto(nomPhoto: String) {
    if (nomPhoto.isBlank()) return
    var voir by remember { mutableStateOf(false) }
    Text(
        "🏞",
        fontSize = 24.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Couleurs.BleuClair)
            .clickable { voir = true }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
    if (voir) VisionneusePhoto(nomPhoto) { voir = false }
}

/** Photo plein écran, zoom à deux doigts. */
@Composable
fun VisionneusePhoto(nomPhoto: String, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(nomPhoto) { Photos.charger(ctx, nomPhoto) }
    var echelle by remember { mutableFloatStateOf(1f) }
    var decalage by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onFermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        echelle = (echelle * zoom).coerceIn(1f, 6f)
                        decalage = if (echelle == 1f) Offset.Zero else decalage + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bmp == null) {
                Text("Photo introuvable", color = Color.White)
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Photo du produit",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = echelle, scaleY = echelle, translationX = decalage.x, translationY = decalage.y),
                )
            }
            Text(
                "✕",
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { onFermer() }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
    }
}

// ---------- Suggestions ----------

/** Noms du catalogue proposés pendant la saisie d'un matériel. */
@Composable
fun SuggestionsProduits(data: AppData, saisie: String, onChoix: (Produit) -> Unit) {
    val sugg = Produits.suggestions(data, saisie)
    if (sugg.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sugg.forEach { p ->
            Text(
                "🧱 " + p.nom,
                fontSize = 13.sp,
                color = Couleurs.Bleu,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Couleurs.BleuClair)
                    .clickable { onChoix(p) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// ---------- Écran Matériel ----------

@Composable
fun EcranMateriel(data: AppData, repo: Repository, recherche: String) {
    var filtre by rememberSaveable { mutableStateOf("") } // id fournisseur ; "" = tous
    var ficheId by rememberSaveable { mutableStateOf<String?>(null) }
    var versCamion by rememberSaveable { mutableStateOf<String?>(null) }

    // Fournisseurs proposés en filtre : ceux des produits et du camion à charger.
    val idsFiltres = (data.produits.map { it.fournisseurId } + data.materiels.filter { !it.recupere }.map { it.fournisseurId })
        .filter { it.isNotBlank() }.distinct()
    val filtres = idsFiltres.mapNotNull { Gestion.fournisseur(data, it) }.sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }
    val produits = Produits.tries(data).filter {
        (filtre.isBlank() || it.fournisseurId == filtre) &&
            Gestion.correspond(recherche, it.nom, it.note, Gestion.fournisseur(data, it.fournisseurId)?.nom)
    }
    val aRecuperer = if (filtre.isBlank()) emptyList()
    else data.materiels.filter { !it.recupere && it.fournisseurId == filtre && Gestion.correspond(recherche, it.nom) }
        .sortedWith { a, b -> Gestion.compareNoms(a.nom, b.nom) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoutonPetit("Tous", if (filtre.isBlank()) Couleurs.Bleu else Couleurs.Gris) { filtre = "" }
                filtres.forEach { f ->
                    BoutonPetit(f.nom.trim(), if (filtre == f.id) Couleurs.Bleu else Couleurs.Gris) { filtre = f.id }
                }
            }
        }
        if (filtre.isNotBlank()) {
            val nomF = Gestion.fournisseur(data, filtre)?.nom?.trim() ?: ""
            item { SectionTitre("🚚 À récupérer chez $nomF (${aRecuperer.size})") }
            if (aRecuperer.isEmpty()) item { Ligne("Rien à récupérer ici pour l'instant.") }
            items(aRecuperer, key = { "c:" + it.id }) { m ->
                Carte(lisere = Couleurs.Rouge) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TitreCarte(m.nom)
                            val ch = Gestion.chantiersDuMateriel(data, m.id)
                            if (ch.isNotEmpty()) Ligne("🏗️ → " + ch.joinToString(", ") { Gestion.nomAffiche(it) })
                        }
                        IconePhoto(Produits.photoDe(data, m.nom))
                        BoutonPetit("🚛 Chargé", Couleurs.Vert) { repo.update { Gestion.basculerCharge(it, m.id, java.time.LocalDate.now()) } }
                    }
                }
            }
            item { SectionTitre("🧱 Produits habituels chez $nomF (${produits.size})") }
        } else {
            item { Ligne("${produits.size} produit" + (if (produits.size > 1) "s" else "") + " · rempli automatiquement avec le camion et l'historique") }
        }
        if (produits.isEmpty()) item { Vide(if (recherche.isBlank() && filtre.isBlank()) "Le catalogue se remplira avec ton camion" else "Aucun produit") }
        items(produits, key = { it.id }) { p ->
            val nbLivre = Produits.historique(data, p).size
            val nbCamion = Produits.dansCamion(data, p).size
            Carte(onClick = { ficheId = p.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TitreCarte(p.nom)
                        Gestion.fournisseur(data, p.fournisseurId)?.let { Ligne("🏭 " + it.nom.trim()) }
                        val infos = listOfNotNull(
                            if (nbLivre > 0) "livré $nbLivre fois" else null,
                            if (nbCamion > 0) "🚚 $nbCamion dans le camion" else null,
                        )
                        if (infos.isNotEmpty()) Ligne(infos.joinToString(" · "))
                    }
                    IconePhoto(p.photo)
                }
                BoutonPetit("➕ Mettre dans le camion", Couleurs.Orange, Modifier.fillMaxWidth().padding(top = 4.dp)) { versCamion = p.id }
            }
        }
    }

    ficheId?.let { id -> Produits.parId(data, id)?.let { p -> FicheProduit(data, repo, p, onCamion = { versCamion = p.id }) { ficheId = null } } }
    versCamion?.let { id -> Produits.parId(data, id)?.let { p -> AjoutCamionProduit(data, repo, p) { versCamion = null } } }
}

@Composable
private fun FicheProduit(data: AppData, repo: Repository, p: Produit, onCamion: () -> Unit, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val confirmation = rememberConfirmation()
    var nom by rememberSaveable(p.id) { mutableStateOf(p.nom) }
    var note by rememberSaveable(p.id) { mutableStateOf(p.note) }
    var choixF by rememberSaveable { mutableStateOf(false) }
    var fusion by rememberSaveable { mutableStateOf(false) }
    var voirPhoto by rememberSaveable { mutableStateOf(false) }

    val photoUri: Uri = remember {
        val dossier = File(ctx.cacheDir, "photos").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", File(dossier, "photo.jpg"))
    }
    fun enregistrerPhoto(uri: Uri) {
        val nomFichier = Photos.importer(ctx, uri, p.id)
        if (nomFichier == null) { Actions.toast(ctx, "❌ Photo impossible"); return }
        val ancienne = p.photo
        repo.update { Produits.changerPhoto(it, p.id, nomFichier) }
        Photos.supprimer(ctx, ancienne)
        Actions.toast(ctx, "✅ Photo enregistrée")
    }
    val prendre = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) enregistrerPhoto(photoUri) }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) enregistrerPhoto(uri) }

    FenetrePleine("🧱 " + p.nom, onFermer) {
        Carte {
            Text("Photo", fontSize = 12.sp, color = Couleurs.Gris)
            if (p.photo.isNotBlank()) {
                BoutonPetit("🏞 Voir la photo", Couleurs.Bleu, Modifier.fillMaxWidth()) { voirPhoto = true }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPetit("📷 Prendre", Couleurs.Vert, Modifier.weight(1f)) { prendre.launch(photoUri) }
                BoutonPetit("🖼️ Galerie", Couleurs.Indigo, Modifier.weight(1f)) { galerie.launch("image/*") }
                if (p.photo.isNotBlank()) BoutonPetit("🗑️", Couleurs.Rouge) {
                    confirmation.demander("Supprimer la photo ?") {
                        Photos.supprimer(ctx, p.photo)
                        repo.update { Produits.changerPhoto(it, p.id, "") }
                    }
                }
            }
        }
        BoutonPlein("➕ Mettre dans le camion", Couleurs.Orange) { onCamion() }

        Carte {
            Champ("Nom", nom, { nom = it })
            ChampChoix("Fournisseur habituel", Gestion.fournisseur(data, p.fournisseurId)?.nom?.trim() ?: "-- Aucun --") { choixF = true }
            Champ("Note (référence, dimension…)", note, { note = it })
            BoutonPetit("Enregistrer", Couleurs.Vert) {
                if (nom.isBlank()) Actions.toast(ctx, "Le nom est obligatoire")
                else { repo.update { Produits.modifier(it, p.id, nom, p.fournisseurId, note) }; Actions.toast(ctx, "✅ Enregistré") }
            }
        }

        val hist = Produits.historique(data, p)
        SectionTitre("Livraisons (${hist.size})")
        if (hist.isEmpty()) Ligne("Pas encore livré.")
        hist.take(30).forEach { a -> Ligne("✅ " + a.dateLivraison + " · " + a.chantiersNom.trim().ifEmpty { "Aucun" } + " · " + a.nom, Color.Unspecified) }

        BoutonContour("🔗 Fusionner avec un autre produit…") { fusion = true }
        BoutonPlein("🗑️ Retirer du catalogue", Couleurs.Rouge) {
            confirmation.demander("Retirer « ${p.nom} » du catalogue ? Il ne reviendra pas tout seul.", "Supprimer") {
                Photos.supprimer(ctx, p.photo)
                repo.update { Produits.supprimer(it, p.id) }
                onFermer()
            }
        }
    }
    if (voirPhoto) VisionneusePhoto(p.photo) { voirPhoto = false }
    if (choixF) ChoixFournisseur(data, { id -> repo.update { Produits.changerFournisseur(it, p.id, id) }; choixF = false }) { choixF = false }
    if (fusion) ChoixDialog(
        "Fusionner « ${p.nom} » dans…",
        Produits.tries(data).filter { it.id != p.id }.map { Option(it.id, it.nom, Gestion.fournisseur(data, it.fournisseurId)?.nom ?: "") },
        onChoix = { cible ->
            fusion = false
            confirmation.demander("« ${p.nom} » sera fusionné dans « ${Produits.parId(data, cible)?.nom} ». Continuer ?", "Fusionner") {
                repo.update { Produits.fusionner(it, p.id, cible) }
                onFermer()
            }
        },
        onFermer = { fusion = false },
    )
    ConfirmationHost(confirmation)
}

/** Ajout rapide au camion depuis le catalogue : quantité, chantier, fournisseur (habituel par défaut). */
@Composable
private fun AjoutCamionProduit(data: AppData, repo: Repository, p: Produit, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var quantite by rememberSaveable { mutableStateOf("") }
    var fournisseurId by rememberSaveable { mutableStateOf(p.fournisseurId) }
    var chantierId by rememberSaveable { mutableStateOf("") }
    var choixF by rememberSaveable { mutableStateOf(false) }
    var choixC by rememberSaveable { mutableStateOf(false) }
    FenetrePleine("➕ " + p.nom + " → camion", onFermer) {
        Champ("Quantité (optionnel)", quantite, { quantite = it.filter { c -> c.isDigit() } }, "ex: 10", clavier = androidx.compose.ui.text.input.KeyboardType.Number)
        ChampChoix("Fournisseur", Gestion.fournisseur(data, fournisseurId)?.nom?.trim() ?: "-- Aucun --") { choixF = true }
        val ch = data.chantiers.firstOrNull { it.id == chantierId }
        ChampChoix("Chantier (optionnel)", ch?.let { Gestion.nomAffiche(it) } ?: "-- Aucun chantier --") { choixC = true }
        BoutonPlein("🚚 Ajouter au camion", Couleurs.Vert) {
            val nomFinal = p.nom + if (quantite.isNotBlank()) " x$quantite" else ""
            repo.update { Gestion.ajouterMateriel(it, nomFinal, fournisseurId, "", chantierId).first }
            Actions.toast(ctx, "✅ $nomFinal ajouté au camion")
            onFermer()
        }
    }
    if (choixF) ChoixFournisseur(data, { fournisseurId = it; choixF = false }) { choixF = false }
    if (choixC) ChoixChantier(data, { chantierId = it; choixC = false }) { choixC = false }
}
