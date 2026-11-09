package com.moussaei.gestion.util

import android.content.Context
import com.moussaei.gestion.core.Coord
import com.moussaei.gestion.core.Trajets
import com.moussaei.gestion.data.Repository
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Calcul d'itinéraire poids lourd avec OpenRouteService (profil driving-hgv).
 * Camion : porteur 26 t, 3,8 m de haut, 2,55 m de large, 10 m de long, 11,5 t par essieu.
 * La clé (gratuite) est rangée dans les réglages du téléphone, jamais dans le code.
 */
object CalculPL {

    private const val PREFS = "reglages"
    private const val CLE = "ors_cle"
    private const val BASE = "https://api.openrouteservice.org"

    const val CAMION = "Porteur 26 t · 3,8 m · 2,55 m · 11,5 t/essieu"

    fun cle(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLE, "").orEmpty()

    fun enregistrerCle(ctx: Context, cle: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CLE, cle.trim()).apply()

    class Erreur(message: String) : Exception(message)

    private fun lire(c: HttpURLConnection): String {
        val code = c.responseCode
        val flux = if (code in 200..299) c.inputStream else c.errorStream
        val texte = flux?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code == 401 || code == 403) throw Erreur("Clé OpenRouteService refusée : vérifie-la dans Réglages.")
        if (code == 429) throw Erreur("Trop de calculs aujourd'hui (limite gratuite atteinte). Réessaie demain.")
        if (code !in 200..299) {
            val msg = try { JSONObject(texte).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
            throw Erreur("Calcul impossible (" + code + ")" + if (!msg.isNullOrBlank()) " : $msg" else "")
        }
        return texte
    }

    /** Coordonnées d'une adresse (géocodage OpenRouteService, centré sur l'Île-de-France). */
    private fun geocoder(cle: String, adresse: String): Coord {
        val url = BASE + "/geocode/search?api_key=" + URLEncoder.encode(cle, "UTF-8") +
            "&text=" + URLEncoder.encode(adresse, "UTF-8") +
            "&boundary.country=FR&focus.point.lat=48.86&focus.point.lon=2.35&size=1"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        try {
            val f = JSONObject(lire(c)).optJSONArray("features")
            if (f == null || f.length() == 0) throw Erreur("Adresse introuvable : $adresse")
            val pt = f.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
            return Coord(pt.getDouble(1), pt.getDouble(0))
        } finally {
            c.disconnect()
        }
    }

    data class Resultat(val distanceM: Int, val dureeS: Int, val geometrie: String, val troncons: List<Int>)

    private fun itineraire(cle: String, points: List<Coord>): Resultat {
        val coords = JSONArray()
        points.forEach { coords.put(JSONArray().put(it.lon).put(it.lat)) }
        val corps = JSONObject()
            .put("coordinates", coords)
            .put("instructions", false)
            .put(
                "options", JSONObject()
                    .put("vehicle_type", "hgv")
                    .put(
                        "profile_params", JSONObject().put(
                            "restrictions", JSONObject()
                                .put("weight", 26).put("height", 3.8).put("width", 2.55)
                                .put("length", 10).put("axleload", 11.5),
                        ),
                    ),
            )
        val c = URL("$BASE/v2/directions/driving-hgv/json").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("Authorization", cle)
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("Accept", "application/json")
        try {
            c.outputStream.use { it.write(corps.toString().toByteArray(Charsets.UTF_8)) }
            val route = JSONObject(lire(c)).getJSONArray("routes").getJSONObject(0)
            val resume = route.getJSONObject("summary")
            val segments = route.optJSONArray("segments")
            val troncons = (0 until (segments?.length() ?: 0)).map { segments!!.getJSONObject(it).optDouble("duration", 0.0).toInt() }
            return Resultat(
                resume.optDouble("distance", 0.0).toInt(),
                resume.optDouble("duration", 0.0).toInt(),
                route.optString("geometry", ""),
                troncons,
            )
        } finally {
            c.disconnect()
        }
    }

    /**
     * Calcule le trajet enregistré [trajetId] et range le résultat dans les données.
     * Les coordonnées trouvées sont mémorisées pour les prochains calculs.
     */
    suspend fun calculer(ctx: Context, repo: Repository, trajetId: String): Resultat = withContext(Dispatchers.IO) {
        val cle = cle(ctx)
        if (cle.isBlank()) throw Erreur("Ajoute ta clé OpenRouteService dans Réglages pour calculer le temps de route.")
        val d = repo.donnees
        val t = Trajets.parId(d, trajetId) ?: throw Erreur("Trajet introuvable")
        val adresses = Trajets.adressesCalcul(d, t)
        if (adresses.size < 2) throw Erreur("Il faut au moins un départ et un arrêt.")
        try {
            val points = adresses.map { a ->
                Trajets.coordonnees(repo.donnees, a) ?: geocoder(cle, a).also { c ->
                    repo.update { Trajets.memoriserGeocode(it, a, c) }
                }
            }
            val r = itineraire(cle, points)
            repo.update { Trajets.enregistrerCalcul(it, trajetId, r.distanceM, r.dureeS, r.geometrie, r.troncons) }
            r
        } catch (e: IOException) {
            throw Erreur("Pas de connexion internet : le calcul se fera plus tard.")
        }
    }

    /** Vérifie la clé en cherchant une adresse connue. */
    suspend fun tester(cle: String): String = withContext(Dispatchers.IO) {
        try {
            geocoder(cle.trim(), "Tour Eiffel, Paris")
            "✅ Clé valide : le temps de route poids lourd sera calculé."
        } catch (e: Erreur) {
            "❌ " + (e.message ?: "Clé refusée")
        } catch (e: IOException) {
            "❌ Pas de connexion internet"
        }
    }

    private val fond = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Calcul en arrière-plan (après « Ouvrir dans Google Maps ») ; sans clé ou sans réseau, on ne fait rien. */
    fun calculerEnFond(ctx: Context, repo: Repository, trajetId: String) {
        val app = ctx.applicationContext
        if (cle(app).isBlank() || trajetId.isBlank()) return
        fond.launch {
            try { calculer(app, repo, trajetId) } catch (e: Exception) { Log.w("Firodi", "Calcul PL : " + e.message) }
        }
    }
}
