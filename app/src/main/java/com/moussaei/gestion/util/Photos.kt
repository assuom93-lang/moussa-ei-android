package com.moussaei.gestion.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Photos des produits : copiées et réduites dans files/photos (incluses dans la sauvegarde Google). */
object Photos {

    private const val TAILLE_MAX = 1600

    fun dossier(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun fichier(ctx: Context, nom: String): File = File(dossier(ctx), nom)

    /** Copie l'image [uri] (appareil photo ou galerie), réduite et bien orientée. Retourne le nom du fichier. */
    fun importer(ctx: Context, uri: Uri, prefixe: String): String? = try {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bornes) }
        var echantillon = 1
        while (maxOf(bornes.outWidth, bornes.outHeight) / echantillon > TAILLE_MAX * 2) echantillon *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = echantillon }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("image illisible")
        val rotation = ctx.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val echelle = minOf(1f, TAILLE_MAX.toFloat() / maxOf(bmp.width, bmp.height))
        if (rotation != 0f || echelle < 1f) {
            val m = Matrix().apply { postScale(echelle, echelle); postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val nom = prefixe + "-" + System.currentTimeMillis() + ".jpg"
        fichier(ctx, nom).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        nom
    } catch (e: Exception) {
        Log.e("MoussaEI", "Photo impossible", e)
        null
    }

    fun charger(ctx: Context, nom: String): Bitmap? = try {
        val f = fichier(ctx, nom)
        if (nom.isBlank() || !f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)
    } catch (e: Throwable) {
        null
    }

    fun supprimer(ctx: Context, nom: String) {
        if (nom.isNotBlank()) fichier(ctx, nom).delete()
    }
}
