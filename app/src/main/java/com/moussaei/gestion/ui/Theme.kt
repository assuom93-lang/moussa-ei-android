package com.moussaei.gestion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Couleurs reprises de la v3.21 (bleu #1e40af) pour garder les repères.
object Couleurs {
    val Bleu = Color(0xFF1E40AF)
    val BleuClair = Color(0xFFDBEAFE)
    val Vert = Color(0xFF059669)
    val VertClair = Color(0xFFD1FAE5)
    val VertFonce = Color(0xFF065F46)
    val Orange = Color(0xFFF59E0B)
    val OrangeClair = Color(0xFFFEF3C7)
    val OrangeFonce = Color(0xFF92400E)
    val Rouge = Color(0xFFDC2626)
    val RougeClair = Color(0xFFFEE2E2)
    val RougeFonce = Color(0xFF991B1B)
    val Indigo = Color(0xFF6366F1)
    val Gris = Color(0xFF6B7280)
    val GrisClair = Color(0xFFF3F4F6)
}

private val Clair = lightColorScheme(
    primary = Couleurs.Bleu,
    onPrimary = Color.White,
    primaryContainer = Couleurs.BleuClair,
    onPrimaryContainer = Couleurs.Bleu,
    secondary = Couleurs.Vert,
    onSecondary = Color.White,
    tertiary = Couleurs.Orange,
    error = Couleurs.Rouge,
    background = Color(0xFFF5F7FB),
    surface = Color.White,
    surfaceVariant = Couleurs.GrisClair,
)

private val Sombre = darkColorScheme(
    primary = Color(0xFF93B4FF),
    onPrimary = Color(0xFF0B1F5C),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF34D399),
    tertiary = Couleurs.Orange,
    error = Color(0xFFF87171),
)

@Composable
fun MoussaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Sombre else Clair, content = content)
}
