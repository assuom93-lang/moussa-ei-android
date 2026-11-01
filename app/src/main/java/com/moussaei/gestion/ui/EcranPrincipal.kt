package com.moussaei.gestion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moussaei.gestion.data.Repository

/** Les parties de l'appli, ouvertes depuis les tuiles de l'accueil. */
enum class Ecran(val icone: String, val titre: String, val avecRecherche: Boolean) {
    TRAJET("🗺️", "Trajet", false),
    CHANTIERS("🏗️", "Chantiers", true),
    FOURNISSEURS("📦", "Fournisseurs", true),
    PALETTES("🪵", "Palettes", true),
    CAMION("🚚", "Camion", true),
    HEURES("⏰", "Heures", false),
    HISTORIQUE("📂", "Historique", true),
    LEXIQUE("📖", "Lexique des agences", true),
    REGLAGES("⚙️", "Réglages", false),
}

@Composable
fun EcranPrincipal(repo: Repository, ecranDemande: Ecran?, onEcranConsomme: () -> Unit) {
    val data by repo.data.collectAsState()
    var ecran by rememberSaveable { mutableStateOf<Ecran?>(null) } // null = accueil
    var recherche by rememberSaveable { mutableStateOf("") }

    fun ouvrir(e: Ecran?, q: String = "") { ecran = e; recherche = q }

    LaunchedEffect(ecranDemande) {
        if (ecranDemande != null) { ouvrir(ecranDemande); onEcranConsomme() }
    }
    BackHandler(enabled = ecran != null) { ouvrir(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // En-tête bleu
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val e = ecran
            if (e == null) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("moussa EI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    if (data.reglages.entreprise.isNotBlank()) {
                        Text(data.reglages.entreprise, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                BoutonEntete("⚙️") { ouvrir(Ecran.REGLAGES) }
            } else {
                BoutonEntete("←") { ouvrir(null) }
                Text(e.icone + " " + e.titre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (e != Ecran.TRAJET) BoutonEntete("🗺️") { ouvrir(Ecran.TRAJET) }
            }
        }

        val e = ecran
        if (e != null && e.avecRecherche) {
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                placeholder = { Text("🔍 Rechercher…") },
                singleLine = true,
                trailingIcon = if (recherche.isNotEmpty()) ({
                    Text("✕", modifier = Modifier.clickable { recherche = "" }.padding(8.dp))
                }) else null,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Column(Modifier.weight(1f).navigationBarsPadding()) {
            when (e) {
                null -> EcranAccueil(data, repo, onOuvrir = { ec, q -> ouvrir(ec, q) })
                Ecran.TRAJET -> EcranTrajet(data, repo)
                Ecran.CHANTIERS -> EcranChantiers(data, repo, recherche)
                Ecran.FOURNISSEURS -> EcranFournisseurs(data, repo, recherche)
                Ecran.PALETTES -> EcranPalettes(data, repo, recherche)
                Ecran.CAMION -> EcranCamion(data, repo, recherche)
                Ecran.HEURES -> EcranHeures(data, repo)
                Ecran.HISTORIQUE -> EcranArchives(data, repo, recherche)
                Ecran.LEXIQUE -> EcranLexique(data, repo, recherche)
                Ecran.REGLAGES -> EcranReglages(data, repo)
            }
        }
    }
}

@Composable
private fun BoutonEntete(texte: String, onClick: () -> Unit) {
    Text(
        texte,
        color = Color.White,
        fontSize = 22.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
