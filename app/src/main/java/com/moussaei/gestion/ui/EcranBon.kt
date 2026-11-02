package com.moussaei.gestion.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Bons
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Produits
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.util.Actions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** Bon reçu par « Partager » depuis une autre appli (WhatsApp, mail, galerie…). */
object BonEntrant {
    var texte by mutableStateOf<String?>(null)
    var uri by mutableStateOf<Uri?>(null)
    var mime by mutableStateOf("")
    val enAttente: Boolean get() = texte != null || uri != null
    fun vider() { texte = null; uri = null; mime = "" }
}

private suspend fun ocrImage(ctx: Context, uri: Uri): String =
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(ctx, uri)).await().text

/** PDF : chaque page (5 max) est dessinée en image puis lue. */
private suspend fun ocrPdf(ctx: Context, uri: Uri): String {
    val images = withContext(Dispatchers.IO) {
        val liste = ArrayList<Bitmap>()
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { r ->
                for (i in 0 until minOf(r.pageCount, 5)) {
                    r.openPage(i).use { page ->
                        val echelle = 2.5f
                        val bmp = Bitmap.createBitmap((page.width * echelle).toInt(), (page.height * echelle).toInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        liste += bmp
                    }
                }
            }
        }
        liste
    }
    val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return images.joinToString("\n") { client.process(InputImage.fromBitmap(it, 0)).await().text }
}

@Composable
fun EcranBon(data: AppData, repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var texte by rememberSaveable { mutableStateOf("") }
    var etat by rememberSaveable { mutableStateOf("") }
    var lignes by rememberSaveable { mutableStateOf(listOf<String>()) }
    var exclus by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var choisis by remember { mutableStateOf(mapOf<Int, String>()) } // ligne → fournisseur choisi
    var changer by rememberSaveable { mutableStateOf<Int?>(null) }
    var chantierId by rememberSaveable { mutableStateOf("") }
    var choixC by rememberSaveable { mutableStateOf(false) }
    var agencesDe by rememberSaveable { mutableStateOf<String?>(null) }

    fun analyser(t: String) {
        texte = t
        lignes = Bons.lignes(t)
        exclus = emptySet()
        choisis = emptyMap()
        etat = if (lignes.isEmpty()) "Aucune ligne de matériel trouvée" else ""
    }
    fun lire(uri: Uri, mime: String) {
        etat = "⏳ Lecture du bon…"
        scope.launch {
            try {
                val t = if (mime == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")) ocrPdf(ctx, uri) else ocrImage(ctx, uri)
                analyser(t)
            } catch (e: Exception) {
                etat = "❌ Lecture impossible : " + (e.message ?: "")
            }
        }
    }

    // Bon partagé depuis une autre appli.
    LaunchedEffect(BonEntrant.texte, BonEntrant.uri) {
        val t = BonEntrant.texte
        val u = BonEntrant.uri
        val m = BonEntrant.mime
        if (t != null || u != null) {
            BonEntrant.vider()
            if (t != null) analyser(t) else if (u != null) lire(u, m)
        }
    }

    val photoUri: Uri = remember {
        val dossier = File(ctx.cacheDir, "photos").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", File(dossier, "bon.jpg"))
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) lire(photoUri, "image/jpeg") }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) lire(u, "image/*") }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) lire(u, "application/pdf") }

    val conseils = lignes.mapIndexed { i, l ->
        val c = Bons.conseil(data, l)
        val f = choisis[i]
        if (f != null) Bons.Conseil(Bons.Raison.HABITUDE, f, Gestion.fournisseur(data, f)?.let { Gestion.enseigneDe(it) } ?: "") else c
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Carte {
            TitreCarte("📄 Lire un bon")
            Ligne("Envoie le bon (photo, PDF ou texte) : l'appli lit chaque ligne et te conseille où aller. Astuce : depuis WhatsApp ou ta galerie, « Partager » → moussa EI.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoutonPetit("📷 Photo", Couleurs.Vert, Modifier.weight(1f)) { photo.launch(photoUri) }
                BoutonPetit("🖼️ Image", Couleurs.Indigo, Modifier.weight(1f)) { image.launch("image/*") }
                BoutonPetit("📄 PDF", Couleurs.Rouge, Modifier.weight(1f)) { pdf.launch(arrayOf("application/pdf")) }
            }
            BoutonPetit("📋 Coller un texte (WhatsApp, SMS…)", Couleurs.Bleu, Modifier.fillMaxWidth()) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                if (t.isBlank()) Actions.toast(ctx, "Le presse-papiers est vide") else analyser(t)
            }
            if (etat.isNotBlank()) Text(etat, fontWeight = FontWeight.SemiBold)
        }

        if (texte.isNotBlank()) {
            Champ("Texte du bon (modifiable)", texte, { texte = it }, multiligne = true)
            BoutonContour("🔍 Relire le texte") { analyser(texte) }
        }

        if (lignes.isNotEmpty()) {
            // Résumé par destination
            val groupes = lignes.indices.filter { it !in exclus }.groupBy { Bons.destination(data, conseils[it]) }
            Carte(lisere = Couleurs.Bleu) {
                TitreCarte("Où aller")
                groupes.forEach { (dest, idx) -> Ligne("🏬 $dest : ${idx.size} article" + (if (idx.size > 1) "s" else ""), Color.Unspecified) }
            }

            lignes.forEachIndexed { i, l ->
                val c = conseils[i]
                val (fond, couleur) = when (c.raison) {
                    Bons.Raison.HABITUDE -> Couleurs.VertClair to Couleurs.VertFonce
                    Bons.Raison.MOT_CLE -> Couleurs.BleuClair to Couleurs.Bleu
                    Bons.Raison.AUCUN -> Couleurs.GrisClair to Couleurs.Gris
                }
                Carte(attenuee = i in exclus) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i !in exclus, onCheckedChange = { on -> exclus = if (on) exclus - i else exclus + i })
                        Column(Modifier.weight(1f)) {
                            Text(l, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (choisis.containsKey(i)) Gestion.fournisseur(data, choisis.getValue(i))?.nom?.trim() + " (choisi)" else Bons.libelle(data, c)),
                                fontSize = 12.sp, color = couleur,
                                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(10.dp)).background(fond).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        IconePhoto(Produits.photoDe(data, l))
                        BoutonPetit("Changer", Couleurs.Gris) { changer = i }
                    }
                }
            }

            val ch = data.chantiers.firstOrNull { it.id == chantierId }
            ChampChoix("Chantier pour ces articles (optionnel)", ch?.let { Gestion.nomAffiche(it) } ?: "-- Aucun chantier --") { choixC = true }
            val inclus = lignes.indices.filter { it !in exclus }
            BoutonPlein("🚚 Mettre dans le camion (${inclus.size})", Couleurs.Vert) {
                if (inclus.isEmpty()) return@BoutonPlein
                repo.update { d ->
                    var x = d
                    inclus.forEach { i ->
                        val c = conseils[i]
                        val note = if (c.fournisseurId.isBlank() && c.enseigne.isNotBlank()) "Conseil : " + c.enseigne else ""
                        x = Gestion.ajouterMateriel(x, lignes[i], c.fournisseurId, note, chantierId).first
                    }
                    x
                }
                Actions.toast(ctx, "✅ ${inclus.size} article" + (if (inclus.size > 1) "s" else "") + " dans le camion")
            }
            // Trajet : fournisseurs précis directement, enseignes sans agence choisie → liste des agences
            val fournisseurs = inclus.map { conseils[it].fournisseurId }.filter { it.isNotBlank() }.distinct()
            val enseignesSeules = inclus.map { conseils[it] }.filter { it.fournisseurId.isBlank() && it.enseigne.isNotBlank() }.map { it.enseigne }.distinct()
            if (fournisseurs.isNotEmpty()) {
                BoutonPlein("🗺️ Ajouter ${fournisseurs.size} fournisseur" + (if (fournisseurs.size > 1) "s" else "") + " au trajet", Couleurs.Bleu) {
                    var n = 0
                    repo.update { d ->
                        var x = d
                        fournisseurs.forEach { id -> Gestion.fournisseur(x, id)?.let { f -> val r = Gestion.ajouterFournisseurAuTrajet(x, f); if (r.second == Gestion.AjoutTrajet.AJOUTE) n++; x = r.first } }
                        x
                    }
                    Actions.toast(ctx, if (n == 0) "Déjà dans le trajet" else "✅ $n ajouté" + (if (n > 1) "s" else "") + " au trajet")
                }
            }
            enseignesSeules.forEach { e ->
                BoutonContour("🏬 Choisir une agence $e (📍 / ➕ Trajet)") { agencesDe = e }
            }
        }
    }

    changer?.let { i ->
        if (i < lignes.size) ChoixFournisseur(data, { id ->
            choisis = choisis + (i to id)
            // L'appli retient ce choix pour la prochaine fois.
            if (id.isNotBlank()) repo.update { Produits.retenir(it, lignes[i], id) }
            changer = null
        }) { changer = null }
    }
    if (choixC) ChoixChantier(data, { chantierId = it; choixC = false }) { choixC = false }
    agencesDe?.let { e -> AgencesDialog(data, repo, e) { agencesDe = null } }
}
