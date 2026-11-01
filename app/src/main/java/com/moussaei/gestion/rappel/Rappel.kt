package com.moussaei.gestion.rappel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.moussaei.gestion.MainActivity
import com.moussaei.gestion.MoussaApp
import com.moussaei.gestion.R
import com.moussaei.gestion.core.Heures
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Rappel quotidien à 15h pour noter ses heures.
 * Contrairement à la v3.21 (minuterie dans la page, perdue quand l'appli était fermée),
 * c'est une vraie notification Android, même appli fermée.
 */
object Rappel {

    private const val CANAL = "rappel_heures"
    private const val TRAVAIL = "rappel_heures_15h"
    private const val PREFS = "reglages"
    private const val CLE_ACTIF = "rappel_actif"
    const val EXTRA_ONGLET = "onglet"

    fun estActif(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CLE_ACTIF, false)

    fun activer(ctx: Context, actif: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CLE_ACTIF, actif).apply()
        if (actif) programmer(ctx) else WorkManager.getInstance(ctx).cancelUniqueWork(TRAVAIL)
    }

    fun programmerSiActif(ctx: Context) {
        if (estActif(ctx)) programmer(ctx)
    }

    fun programmer(ctx: Context) {
        val maintenant = LocalDateTime.now()
        var cible = LocalDate.now().atTime(LocalTime.of(15, 0))
        if (!maintenant.isBefore(cible)) cible = cible.plusDays(1)
        val delai = Duration.between(maintenant, cible).toMillis().coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<RappelWorker>()
            .setInitialDelay(delai, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(TRAVAIL, ExistingWorkPolicy.REPLACE, req)
    }

    fun creerCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Rappel des heures", NotificationManager.IMPORTANCE_HIGH)
            canal.description = "Rappel à 15h pour noter tes heures de travail"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    fun notifier(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val heures = MoussaApp.instance.repo.donnees.heures
        val jour = Heures.dernierJourARemplir(heures, LocalDate.now())
        if (heures[jour.toString()]?.remplie == true) return // tout est déjà noté

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ONGLET, "heures")
        }
        val pi = PendingIntent.getActivity(ctx, 15, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Pense à noter tes heures")
            .setContentText("Jour à remplir : " + Heures.titreJour(jour))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(15, notif)
    }
}

class RappelWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        if (Rappel.estActif(applicationContext)) {
            try { Rappel.notifier(applicationContext) } catch (e: SecurityException) { /* permission retirée */ }
            Rappel.programmer(applicationContext) // demain 15h
        }
        return Result.success()
    }
}
