package com.moussaei.gestion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Csv
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.rappel.Rappel
import com.moussaei.gestion.util.Actions
import java.io.File
import java.time.LocalDate

@Composable
fun EcranReglages(data: AppData, repo: Repository) {
    val ctx = LocalContext.current
    var rappel by remember { mutableStateOf(Rappel.estActif(ctx)) }
    var importEnAttente by remember { mutableStateOf<Csv.Import?>(null) }
    var voirSauvegardes by rememberSaveable { mutableStateOf(false) }

    fun activerRappel() {
        Rappel.activer(ctx, true)
        rappel = true
        Actions.toast(ctx, "✅ Rappel activé à 15h00 pour noter tes heures")
    }

    val permissionNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) activerRappel() else Actions.toast(ctx, "Sans l'autorisation des notifications, le rappel ne peut pas s'afficher")
    }

    fun preparerImport(texte: String) {
        val imp = Csv.import(texte)
        if (imp == null || imp.estVide) Actions.toast(ctx, "❌ Impossible de lire les données. Choisis un fichier de sauvegarde moussa EI (.csv).")
        else importEnAttente = imp
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Csv.export(repo.donnees).toByteArray(Charsets.UTF_8)) }
                Actions.toast(ctx, "✅ Sauvegarde enregistrée")
            } catch (e: Exception) {
                Actions.toast(ctx, "❌ Enregistrement impossible : " + (e.message ?: ""))
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val texte = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                preparerImport(texte)
            } catch (e: Exception) {
                Actions.toast(ctx, "❌ Fichier illisible")
            }
        }
    }

    val nomFichier = "moussa-ei-" + LocalDate.now() + ".csv"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Carte {
            TitreCarte("⏰ Rappel heures de travail")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rappel 15h", fontWeight = FontWeight.SemiBold)
                    Ligne(if (rappel) "⏰ Notification chaque jour à 15h00 s'il reste un jour à remplir" else "❌ Rappel désactivé")
                }
                Switch(checked = rappel, onCheckedChange = { on ->
                    if (!on) { Rappel.activer(ctx, false); rappel = false }
                    else if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permissionNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else activerRappel()
                })
            }
        }

        Carte {
            TitreCarte("💾 Mes données")
            Ligne("${data.chantiers.size} chantiers · ${data.fournisseurs.size} fournisseurs · ${data.materiels.size} matériels · ${data.archives.size} archives · ${data.palettes.size} palettes · ${data.heures.size} jours d'heures")
            BoutonPlein("📤 Enregistrer une sauvegarde (Drive, fichiers…)", Couleurs.Indigo) { exporter.launch(nomFichier) }
            BoutonPlein("📲 Envoyer la sauvegarde (WhatsApp, mail…)", Couleurs.Bleu) {
                try {
                    val dossier = File(ctx.cacheDir, "partage").apply { mkdirs() }
                    val f = File(dossier, nomFichier)
                    f.writeText(Csv.export(repo.donnees), Charsets.UTF_8)
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", f)
                    Actions.partagerFichier(ctx, uri, "text/csv", "Sauvegarde moussa EI")
                } catch (e: Exception) {
                    Actions.toast(ctx, "❌ Partage impossible")
                }
            }
            BoutonPlein("📥 Importer un fichier de sauvegarde", Couleurs.Vert) { importer.launch(arrayOf("*/*")) }

            Ligne("Pour récupérer tes données : « Importer » puis choisis le fichier de sauvegarde dans Google Drive.")
        }

        Carte {
            TitreCarte("☁️ Sauvegarde Google")
            Ligne("Tes données sont sauvegardées automatiquement sur ton compte Google (Google Drive) par Android, environ une fois par jour quand le téléphone charge en Wi-Fi. Si tu réinstalles l'appli ou changes de téléphone avec le même compte Google, elles reviennent toutes seules.")
            Ligne("À vérifier une fois : Paramètres du téléphone → Google → Sauvegarde → « Sauvegarde par Google One » activée.")
            Ligne("En plus, l'appli garde une copie de chaque jour (7 derniers jours) sur le téléphone.")
            BoutonContour("🗂️ Copies des 7 derniers jours") { voirSauvegardes = true }
        }

        Carte {
            TitreCarte("ℹ️ À propos")
            val version = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            } catch (e: Exception) { "" }
            Ligne("moussa EI v$version — application native Android\nGestion des chantiers, du camion et des livraisons.\nToutes les données restent sur ton téléphone (et ta sauvegarde Google).")
        }
    }

    importEnAttente?.let { imp ->
        val n = imp.data
        Confirmer(
            message = "Importer : ${n.chantiers.size} chantiers, ${n.fournisseurs.size} fournisseurs, ${n.archives.size} archives, ${n.palettes.size} palettes, ${n.heures.size} jours d'heures" +
                (if (imp.contientMateriels) ", ${n.materiels.size} matériels" else "") +
                ".\n\nTes chantiers, fournisseurs et archives actuels seront remplacés. Continuer ?",
            libelleOk = "Importer",
            couleurOk = Couleurs.Vert,
            onOk = {
                repo.update { Gestion.importer(it, imp) }
                importEnAttente = null
                Actions.toast(ctx, "✅ Données importées !")
            },
            onAnnuler = { importEnAttente = null },
        )
    }

    if (voirSauvegardes) {
        val confirmation = rememberConfirmation()
        FenetrePleine("🗂️ Copies des 7 derniers jours", { voirSauvegardes = false }) {
            val fichiers = repo.sauvegardesLocales()
            if (fichiers.isEmpty()) Vide("Aucune copie pour l'instant")
            fichiers.forEach { f ->
                Carte {
                    TitreCarte(f.name.removePrefix("moussa-ei-").removeSuffix(".csv"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoutonPetit("↩ Restaurer", Couleurs.Orange, Modifier.weight(1f)) {
                            val imp = Csv.import(f.readText(Charsets.UTF_8))
                            if (imp == null) Actions.toast(ctx, "❌ Copie illisible")
                            else confirmation.demander("Revenir aux données de cette copie ? Les données actuelles seront remplacées.", "Restaurer") {
                                repo.update { imp.data }
                                Actions.toast(ctx, "✅ Copie restaurée")
                                voirSauvegardes = false
                            }
                        }
                        BoutonPetit("📲 Envoyer", Couleurs.Bleu, Modifier.weight(1f)) {
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", f)
                            Actions.partagerFichier(ctx, uri, "text/csv", "Sauvegarde moussa EI")
                        }
                    }
                }
            }
        }
        ConfirmationHost(confirmation)
    }
}
