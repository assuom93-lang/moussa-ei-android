package com.moussaei.gestion

import android.app.Application
import android.os.Build
import android.util.Log
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.rappel.Rappel
import java.io.File
import java.time.LocalDateTime

open class MoussaApp : Application() {

    lateinit var repo: Repository
        private set

    /** Erreur survenue pendant le démarrage (affichée par MainActivity au lieu de planter). */
    var erreurDemarrage: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installerRapportDePlantage()
        try {
            repo = Repository(this)
            repo.sauvegardeDuJour()
        } catch (e: Throwable) {
            Log.e("MoussaEI", "Démarrage impossible", e)
            erreurDemarrage = e
            ecrireRapport(e, "démarrage")
            return
        }
        try {
            Rappel.creerCanal(this)
            Rappel.programmerSiActif(this)
        } catch (e: Throwable) {
            // Le rappel ne doit jamais empêcher l'appli de s'ouvrir.
            Log.e("MoussaEI", "Rappel indisponible", e)
            ecrireRapport(e, "rappel")
        }
    }

    val fichierRapport: File get() = File(filesDir, "plantage.txt")

    private fun installerRapportDePlantage() {
        val precedent = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { ecrireRapport(e, "thread " + t.name) } catch (ignore: Throwable) {}
            precedent?.uncaughtException(t, e)
        }
    }

    fun ecrireRapport(e: Throwable, contexte: String) {
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (ignore: Throwable) { "?" }
        val texte = buildString {
            append("moussa EI ").append(version).append(" — ").append(LocalDateTime.now()).append('\n')
            append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Contexte : ").append(contexte).append("\n\n")
            append(Log.getStackTraceString(e))
        }
        fichierRapport.writeText(texte)
    }

    companion object {
        lateinit var instance: MoussaApp
            private set
    }
}
