package com.moussaei.gestion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.moussaei.gestion.rappel.Rappel
import com.moussaei.gestion.ui.EcranPrincipal
import com.moussaei.gestion.ui.MoussaTheme
import com.moussaei.gestion.ui.Onglet

class MainActivity : ComponentActivity() {

    private val ongletDemande = mutableStateOf<Onglet?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lireIntent(intent)
        setContent {
            MoussaTheme {
                EcranPrincipal(
                    repo = MoussaApp.instance.repo,
                    ongletDemande = ongletDemande.value,
                    onOngletConsomme = { ongletDemande.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lireIntent(intent)
    }

    private fun lireIntent(intent: Intent?) {
        val o = intent?.getStringExtra(Rappel.EXTRA_ONGLET) ?: return
        ongletDemande.value = Onglet.entries.firstOrNull { it.name.equals(o, ignoreCase = true) }
    }
}
