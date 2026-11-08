package com.moussaei.gestion.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Ouvertures vers les autres applis du téléphone (Maps, téléphone, partage). */
object Actions {

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun lancer(ctx: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun ouvrirUrl(ctx: Context, url: String) {
        if (!lancer(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) toast(ctx, "Aucune appli pour ouvrir ce lien")
    }

    /** Adresse dans Google Maps (appli si installée, sinon navigateur). */
    fun ouvrirAdresse(ctx: Context, adresse: String) {
        if (adresse.isBlank()) return
        ouvrirUrl(ctx, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(adresse))
    }

    fun appeler(ctx: Context, tel: String) {
        val num = tel.filter { it.isDigit() || it == '+' }
        if (num.length < 3) { toast(ctx, "Numéro incomplet"); return }
        if (!lancer(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))) toast(ctx, "Impossible d'ouvrir le téléphone")
    }

    fun copier(ctx: Context, texte: String, message: String = "✅ Copié !") {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Firodi", texte))
        toast(ctx, message)
    }

    fun partagerTexte(ctx: Context, titre: String, texte: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, titre)
            putExtra(Intent.EXTRA_TEXT, texte)
        }
        lancer(ctx, Intent.createChooser(send, titre))
    }

    fun partagerFichier(ctx: Context, uri: Uri, mime: String, titre: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, titre)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        lancer(ctx, Intent.createChooser(send, titre))
    }
}
