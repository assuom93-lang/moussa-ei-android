package com.moussaei.gestion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.STATUT_TERMINE
import com.moussaei.gestion.util.Actions

private data class Lieu(val cle: String, val arret: Gestion.Arret)

@Composable
fun ItineraireDialog(data: AppData, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val choisis = remember { mutableStateListOf<String>() } // clés, dans l'ordre du trajet

    val lieux = remember(data) {
        (data.chantiers.filter { it.statut != STATUT_TERMINE }.map { c ->
            Lieu("c" + c.id, Gestion.Arret(Gestion.nomAffiche(c), c.adresse, true))
        } + data.fournisseurs.map { f ->
            Lieu("f" + f.id, Gestion.Arret(f.nom.trim(), f.adresse, false))
        }).sortedWith { a, b -> Gestion.compareNoms(a.arret.nom, b.arret.nom) }
    }
    val parCle = lieux.associateBy { it.cle }
    val arrets = choisis.mapNotNull { parCle[it]?.arret }

    FenetrePleine("🗺️ Itinéraire", onFermer) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            placeholder = { Text("🔍 Rechercher chantier / fournisseur…") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Ligne("Coche les arrêts dans l'ordre de ton trajet.")

        if (arrets.isNotEmpty()) {
            val min = Gestion.minutesEstimees(arrets)
            Carte(lisere = Couleurs.Vert) {
                Text("📍 ${arrets.size} arrêt" + (if (arrets.size > 1) "s" else "") + " — ~" + Gestion.dureeTexte(min) + " de trajet", fontWeight = FontWeight.Bold)
                arrets.forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Text((i + 1).toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("  " + a.nom, fontWeight = FontWeight.SemiBold)
                    }
                    if (i < arrets.size - 1) {
                        Ligne("        🚛 ~" + (if (arrets[i + 1].estChantier) 45 else 30) + " min")
                    }
                }
            }
            BoutonPlein("🚀 Ouvrir dans Google Maps", Couleurs.Vert) {
                if (arrets.size < 2) Actions.toast(ctx, "Sélectionne au moins 2 arrêts")
                else Actions.ouvrirUrl(ctx, Gestion.urlGoogleMaps(arrets))
            }
        }

        lieux.filter { Gestion.correspond(q, it.arret.nom, it.arret.adresse) }.forEach { l ->
            val rang = choisis.indexOf(l.cle)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (rang >= 0) Couleurs.VertClair else MaterialTheme.colorScheme.surface)
                    .clickable { if (rang >= 0) choisis.remove(l.cle) else choisis.add(l.cle) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rang >= 0, onCheckedChange = { if (it) choisis.add(l.cle) else choisis.remove(l.cle) })
                Text(if (l.arret.estChantier) "🏗️" else "📦", fontSize = 18.sp)
                Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(l.arret.nom, fontWeight = FontWeight.SemiBold, color = if (rang >= 0) Couleurs.VertFonce else Color.Unspecified)
                    Text(l.arret.adresse, fontSize = 12.sp, color = Couleurs.Gris)
                }
                if (rang >= 0) Text("${rang + 1}", fontWeight = FontWeight.Bold, color = Couleurs.VertFonce, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}
