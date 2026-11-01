package com.moussaei.gestion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.util.Actions

// ---------- Mise en page ----------

/** Fenêtre plein écran (fiche, formulaire). */
@Composable
fun FenetrePleine(titre: String, onFermer: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onFermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        titre,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "✕",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFermer() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        }
    }
}

/** Carte de liste, avec liseré gauche coloré optionnel (en livraison, chargé...). */
@Composable
fun Carte(
    lisere: Color? = null,
    attenuee: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val forme = RoundedCornerShape(14.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (attenuee) 0.55f else 1f)
            .clip(forme)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = forme,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { if (lisere != null) drawRect(lisere, size = Size(5.dp.toPx(), size.height)) }
                .padding(start = if (lisere != null) 18.dp else 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
fun TitreCarte(texte: String, sousTitre: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texte, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        if (sousTitre.isNotBlank()) {
            Text(" — $sousTitre", fontSize = 15.sp, color = Couleurs.Gris)
        }
    }
}

@Composable
fun Ligne(texte: String, couleur: Color = Couleurs.Gris) {
    Text(texte, fontSize = 13.sp, color = couleur)
}

@Composable
fun Note(texte: String) {
    if (texte.isBlank()) return
    Text(
        "📝 $texte",
        fontSize = 13.sp,
        color = Couleurs.OrangeFonce,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Couleurs.OrangeClair)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Adresse cliquable → Google Maps. */
@Composable
fun LienAdresse(adresse: String, avecCopie: Boolean = false) {
    if (adresse.isBlank()) return
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "📍 $adresse",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable { Actions.ouvrirAdresse(ctx, adresse) },
        )
        if (avecCopie) {
            Text(
                "📋",
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { Actions.copier(ctx, adresse, "✅ Adresse copiée") }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Téléphone cliquable → composeur. */
@Composable
fun LienTel(tel: String) {
    if (tel.isBlank()) return
    val ctx = LocalContext.current
    Text(
        "📞 $tel",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { Actions.appeler(ctx, tel) },
    )
}

@Composable
fun Pastille(texte: String, fond: Color, couleur: Color, modifier: Modifier = Modifier) {
    Text(
        texte,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = couleur,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fond)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun Pastilles(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
fun SectionTitre(texte: String) {
    Text(
        texte,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun Vide(texte: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(texte, color = Couleurs.Gris)
    }
}

// ---------- Boutons ----------

@Composable
fun BoutonPlein(texte: String, couleur: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = couleur, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(texte, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun BoutonPetit(texte: String, couleur: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = couleur, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(texte, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun BoutonContour(texte: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(10.dp)) {
        Text(texte)
    }
}

/** Petite poubelle ronde (comme la v3.21). */
@Composable
fun BoutonPoubelle(onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Couleurs.RougeClair)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text("🗑️", fontSize = 16.sp) }
}

// ---------- Champs ----------

@Composable
fun Champ(
    label: String,
    valeur: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    multiligne: Boolean = false,
    clavier: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = Couleurs.Gris) }) else null,
        singleLine = !multiligne,
        minLines = if (multiligne) 3 else 1,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = clavier),
        modifier = modifier,
    )
}

/** Champ de choix (ouvre une liste avec recherche). */
@Composable
fun ChampChoix(label: String, valeurAffichee: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = Couleurs.Gris, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(valeurAffichee, modifier = Modifier.weight(1f))
            Text("▾", color = Couleurs.Gris)
        }
    }
}

data class Option(val id: String, val libelle: String, val detail: String = "")

/** Liste de choix avec recherche (fournisseurs, chantiers...). */
@Composable
fun ChoixDialog(
    titre: String,
    options: List<Option>,
    libelleAucun: String? = null,
    onChoix: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onFermer) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).heightIn(max = 560.dp)) {
                Text(titre, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                if (options.size > 6) {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { q = it },
                        placeholder = { Text("🔍 Rechercher…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                val filtrees = options.filter { Gestion.correspond(q, it.libelle, it.detail) }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (libelleAucun != null && q.isBlank()) {
                        item {
                            Text(
                                libelleAucun,
                                color = Couleurs.Gris,
                                modifier = Modifier.fillMaxWidth().clickable { onChoix("") }.padding(vertical = 12.dp, horizontal = 4.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                    items(filtrees, key = { it.id }) { o ->
                        Column(Modifier.fillMaxWidth().clickable { onChoix(o.id) }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                            Text(o.libelle, fontWeight = FontWeight.SemiBold)
                            if (o.detail.isNotBlank()) Text(o.detail, fontSize = 12.sp, color = Couleurs.Gris)
                        }
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = onFermer, modifier = Modifier.align(Alignment.End)) { Text("Fermer") }
            }
        }
    }
}

// ---------- Confirmation ----------

@Composable
fun Confirmer(message: String, libelleOk: String = "Supprimer", couleurOk: Color = Couleurs.Rouge, onOk: () -> Unit, onAnnuler: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        text = { Text(message, fontSize = 16.sp) },
        confirmButton = {
            Button(onClick = onOk, colors = ButtonDefaults.buttonColors(containerColor = couleurOk, contentColor = Color.White)) { Text(libelleOk) }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
}

/** État d'une confirmation en attente : message + action. */
class ConfirmationState {
    var message by mutableStateOf<String?>(null)
        private set
    var libelleOk by mutableStateOf("Supprimer")
        private set
    private var action: (() -> Unit)? = null

    fun demander(message: String, libelleOk: String = "Supprimer", action: () -> Unit) {
        this.message = message
        this.libelleOk = libelleOk
        this.action = action
    }

    fun valider() { val a = action; fermer(); a?.invoke() }
    fun fermer() { message = null; action = null }
}

@Composable
fun rememberConfirmation(): ConfirmationState = remember { ConfirmationState() }

@Composable
fun ConfirmationHost(state: ConfirmationState) {
    val m = state.message ?: return
    Confirmer(
        message = m,
        libelleOk = state.libelleOk,
        couleurOk = if (state.libelleOk == "Supprimer" || state.libelleOk == "Effacer") Couleurs.Rouge else Couleurs.Vert,
        onOk = { state.valider() },
        onAnnuler = { state.fermer() },
    )
}
