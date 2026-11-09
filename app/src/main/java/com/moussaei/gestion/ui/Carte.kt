package com.moussaei.gestion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.moussaei.gestion.core.Coord
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** Point numéroté sur la carte. */
data class PointCarte(val coord: Coord, val libelle: String, val titre: String, val couleur: Int)

private fun preparerOsm(ctx: Context) {
    val conf = Configuration.getInstance()
    conf.userAgentValue = ctx.packageName
    conf.osmdroidBasePath = File(ctx.filesDir, "osmdroid")
    conf.osmdroidTileCache = File(ctx.cacheDir, "osmdroid-tuiles")
}

private fun pastille(ctx: Context, texte: String, couleur: Int): BitmapDrawable {
    val dp = ctx.resources.displayMetrics.density
    val taille = (30 * dp).toInt()
    val bmp = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(taille / 2f, taille / 2f, taille / 2f, p)
    p.color = couleur
    c.drawCircle(taille / 2f, taille / 2f, taille / 2f - 2 * dp, p)
    p.color = android.graphics.Color.WHITE
    p.textSize = 13 * dp
    p.textAlign = Paint.Align.CENTER
    p.isFakeBoldText = true
    c.drawText(texte, taille / 2f, taille / 2f - (p.descent() + p.ascent()) / 2, p)
    return BitmapDrawable(ctx.resources, bmp)
}

/** Carte OpenStreetMap avec le tracé et les arrêts numérotés. */
@Composable
fun CarteTrajet(points: List<PointCarte>, trace: List<Coord>, modifier: Modifier = Modifier.fillMaxWidth().height(300.dp)) {
    val ctx = LocalContext.current
    val vue = remember {
        preparerOsm(ctx)
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(48.86, 2.35))
            // Dans une page qui défile : la carte garde le doigt (déplacer / zoomer).
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
        }
    }
    DisposableEffect(vue) {
        vue.onResume()
        onDispose { vue.onPause(); vue.onDetach() }
    }
    AndroidView(
        factory = { vue },
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        update = { m ->
            m.overlays.clear()
            val tous = ArrayList<GeoPoint>()
            if (trace.size >= 2) {
                val ligne = Polyline(m)
                ligne.setPoints(trace.map { GeoPoint(it.lat, it.lon) })
                ligne.outlinePaint.color = android.graphics.Color.parseColor("#1E40AF")
                ligne.outlinePaint.strokeWidth = 9f
                m.overlays.add(ligne)
                tous += ligne.actualPoints
            }
            points.forEach { pt ->
                val g = GeoPoint(pt.coord.lat, pt.coord.lon)
                val mk = Marker(m)
                mk.position = g
                mk.title = pt.titre
                mk.icon = pastille(ctx, pt.libelle, pt.couleur)
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                m.overlays.add(mk)
                tous += g
            }
            if (tous.size >= 2) {
                val bb = BoundingBox.fromGeoPointsSafe(tous)
                m.post { m.zoomToBoundingBox(bb.increaseByScale(1.25f), false) }
            } else if (tous.size == 1) {
                m.controller.setZoom(14.0); m.controller.setCenter(tous[0])
            }
            m.invalidate()
        },
    )
}
