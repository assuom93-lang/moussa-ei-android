package com.moussaei.gestion

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.moussaei.gestion.rappel.Rappel
import com.moussaei.gestion.ui.BonEntrant
import com.moussaei.gestion.ui.Ecran
import com.moussaei.gestion.ui.EcranPrincipal
import com.moussaei.gestion.ui.MoussaTheme

class MainActivity : ComponentActivity() {

    private val ecranDemande = mutableStateOf<Ecran?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = MoussaApp.instance
        // Plantage précédent ou démarrage impossible : on montre le rapport au lieu de replanter.
        if (app.erreurDemarrage != null || app.fichierRapport.exists()) {
            afficherRapport(app)
            return
        }
        enableEdgeToEdge()
        lireIntent(intent)
        setContent {
            MoussaTheme {
                EcranPrincipal(
                    repo = app.repo,
                    ecranDemande = ecranDemande.value,
                    onEcranConsomme = { ecranDemande.value = null },
                )
            }
        }
    }

    /** Écran de secours en vues Android simples (sans Compose), pour envoyer le rapport. */
    private fun afficherRapport(app: MoussaApp) {
        val rapport = try { app.fichierRapport.readText() } catch (e: Exception) { app.erreurDemarrage?.toString() ?: "" }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            setBackgroundColor(Color.WHITE)
        }
        racine.addView(TextView(this).apply {
            text = "⚠️ moussa EI s'est arrêtée"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E40AF"))
        })
        racine.addView(TextView(this).apply {
            text = "Envoie ce rapport pour que le problème soit corrigé. Tes données ne sont pas effacées."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, pad / 2, 0, pad / 2)
        })
        racine.addView(Button(this).apply {
            text = "📤 Envoyer le rapport"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Rapport de plantage moussa EI")
                    putExtra(Intent.EXTRA_TEXT, rapport)
                }
                startActivity(Intent.createChooser(send, "Envoyer le rapport"))
            }
        })
        if (app.erreurDemarrage == null) {
            racine.addView(Button(this).apply {
                text = "▶ Relancer l'appli"
                setOnClickListener {
                    app.fichierRapport.delete()
                    recreate()
                }
            })
        }
        racine.addView(ScrollView(this).apply {
            addView(TextView(this@MainActivity).apply {
                text = rapport
                textSize = 11f
                setTextIsSelectable(true)
                setTextColor(Color.BLACK)
                typeface = Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(racine)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lireIntent(intent)
    }

    private fun lireIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            // Bon partagé depuis WhatsApp, mail, galerie… : photo, PDF ou texte.
            val uri: android.net.Uri? = androidx.core.content.IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, android.net.Uri::class.java,
            )
            if (uri != null) {
                BonEntrant.mime = intent.type ?: ""
                BonEntrant.uri = uri
            } else {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { BonEntrant.texte = it }
            }
            return
        }
        val o = intent?.getStringExtra(Rappel.EXTRA_ONGLET) ?: return
        ecranDemande.value = Ecran.entries.firstOrNull { it.name.equals(o, ignoreCase = true) }
    }
}
