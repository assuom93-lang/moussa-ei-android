package com.moussaei.gestion.data

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Csv
import com.moussaei.gestion.core.Gestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate

/**
 * Stockage des données de l'appli.
 *
 * Les données vivent en mémoire (StateFlow) et sont écrites dans files/donnees.csv à chaque
 * modification, de façon atomique (fichier temporaire puis renommage : jamais de fichier à moitié écrit).
 * Une copie datée est gardée chaque jour dans files/sauvegardes (7 derniers jours).
 * Le dossier files est inclus dans la sauvegarde automatique Android vers Google Drive.
 */
class Repository(context: Context) {

    private val appContext = context.applicationContext
    private val dir: File = context.filesDir
    private val fichier = AtomicFile(File(dir, "donnees.csv"))
    private val dossierSauvegardes = File(dir, "sauvegardes")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ecriture = Mutex()

    /** Agences Île-de-France des grandes enseignes (assets/agences_idf.csv). */
    val catalogue: List<Gestion.AgenceCatalogue> by lazy {
        try {
            appContext.assets.open("agences_idf.csv").use { Gestion.lireCatalogue(String(it.readBytes(), Charsets.UTF_8)) }
        } catch (e: Exception) {
            Log.e(TAG, "Catalogue des agences illisible", e)
            emptyList()
        }
    }

    private val _data = MutableStateFlow(charger())
    val data: StateFlow<AppData> = _data.asStateFlow()

    val donnees: AppData get() = _data.value

    private fun charger(): AppData = try {
        val f = File(dir, "donnees.csv")
        if (!f.exists()) AppData(reglages = com.moussaei.gestion.core.Reglages(version = com.moussaei.gestion.core.VERSION_DONNEES))
        else Csv.import(String(fichier.readFully(), Charsets.UTF_8))?.data ?: AppData()
    } catch (e: Exception) {
        Log.e(TAG, "Lecture des données impossible", e)
        AppData()
    }

    init {
        // Mise à niveau des données (v4.2 : enseignes, agences, dépôt) dès le démarrage.
        if (_data.value.reglages.version < com.moussaei.gestion.core.VERSION_DONNEES) update { it }
    }

    /** Applique une modification puis enregistre. */
    fun update(transform: (AppData) -> AppData) {
        // migrer() ne fait rien si les données sont déjà à jour ; sinon (import d'un ancien
        // fichier) il complète enseignes et agences.
        // + le catalogue « Matériel » se remplit tout seul avec le camion et l'historique.
        _data.update { com.moussaei.gestion.core.Produits.synchroniser(Gestion.migrer(transform(it), catalogue)) }
        enregistrer()
    }

    private fun enregistrer() {
        scope.launch {
            ecriture.withLock {
                val texte = Csv.export(_data.value)
                var out: java.io.FileOutputStream? = null
                try {
                    out = fichier.startWrite()
                    out.write(texte.toByteArray(Charsets.UTF_8))
                    fichier.finishWrite(out)
                } catch (e: Exception) {
                    Log.e(TAG, "Écriture impossible", e)
                    if (out != null) fichier.failWrite(out)
                }
            }
        }
    }

    /** Copie du jour (une par jour, 7 gardées). Appelé au démarrage. */
    fun sauvegardeDuJour() {
        scope.launch {
            ecriture.withLock {
                try {
                    val d = _data.value
                    if (d.chantiers.isEmpty() && d.fournisseurs.isEmpty()) return@withLock
                    dossierSauvegardes.mkdirs()
                    val f = File(dossierSauvegardes, "moussa-ei-" + LocalDate.now() + ".csv")
                    if (!f.exists()) f.writeText(Csv.export(d), Charsets.UTF_8)
                    dossierSauvegardes.listFiles { x -> x.name.endsWith(".csv") }
                        ?.sortedByDescending { it.name }
                        ?.drop(7)
                        ?.forEach { it.delete() }
                } catch (e: Exception) {
                    Log.e(TAG, "Sauvegarde du jour impossible", e)
                }
            }
        }
    }

    fun sauvegardesLocales(): List<File> =
        dossierSauvegardes.listFiles { x -> x.name.endsWith(".csv") }?.sortedByDescending { it.name } ?: emptyList()

    companion object {
        private const val TAG = "MoussaEI"
    }
}
