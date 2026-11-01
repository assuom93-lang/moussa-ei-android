package com.moussaei.gestion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.data.Repository

enum class Onglet(val icone: String, val libelle: String, val avecRecherche: Boolean) {
    CHANTIERS("🏗️", "Chantiers", true),
    CAMION("🚚", "Camion", true),
    FOURNISSEURS("📦", "Fourn.", true),
    PALETTES("🪵", "Palettes", true),
    HEURES("⏰", "Heures", false),
    ARCHIVES("📂", "Archives", true),
    OUTILS("🔧", "Outils", false),
    REGLAGES("⚙️", "Réglages", false),
}

@Composable
fun EcranPrincipal(repo: Repository, ongletDemande: Onglet?, onOngletConsomme: () -> Unit) {
    val data by repo.data.collectAsState()
    var onglet by rememberSaveable { mutableStateOf(Onglet.CHANTIERS) }
    var recherche by rememberSaveable { mutableStateOf("") }
    var itineraire by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ongletDemande) {
        if (ongletDemande != null) {
            onglet = ongletDemande
            onOngletConsomme()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // En-tête bleu
        Column(Modifier.background(MaterialTheme.colorScheme.primary).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("moussa EI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                Text(
                    "🗺️",
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { itineraire = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            ScrollableTabRow(
                selectedTabIndex = onglet.ordinal,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                edgePadding = 4.dp,
            ) {
                Onglet.entries.forEach { o ->
                    val badge = if (o == Onglet.CAMION) Gestion.nbACharger(data) else 0
                    Tab(
                        selected = onglet == o,
                        onClick = { onglet = o; recherche = "" },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(o.icone, fontSize = 18.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.libelle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    if (badge > 0) {
                                        Text(
                                            badge.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Couleurs.Rouge)
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        },
                        selectedContentColor = Color.White,
                        unselectedContentColor = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }

        if (onglet.avecRecherche) {
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
            when (onglet) {
                Onglet.CHANTIERS -> EcranChantiers(data, repo, recherche)
                Onglet.CAMION -> EcranCamion(data, repo, recherche)
                Onglet.FOURNISSEURS -> EcranFournisseurs(data, repo, recherche)
                Onglet.PALETTES -> EcranPalettes(data, repo, recherche)
                Onglet.HEURES -> EcranHeures(data, repo)
                Onglet.ARCHIVES -> EcranArchives(data, repo, recherche)
                Onglet.OUTILS -> EcranOutils(data, repo)
                Onglet.REGLAGES -> EcranReglages(data, repo)
            }
        }
    }

    if (itineraire) ItineraireDialog(data) { itineraire = false }
}
