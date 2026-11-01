package com.moussaei.gestion.core

// Modèle de données de moussa EI.
// Les champs reprennent exactement ceux de la v3.21 (WebView) pour que le CSV
// exporté par l'ancienne appli s'importe tel quel.

const val STATUT_EN_COURS = "en_cours"
const val STATUT_TERMINE = "termine"

const val EMPLACEMENT_CABINE = "cabine"
const val EMPLACEMENT_PLATEAU = "plateau"

data class Chantier(
    val id: String,
    val nom: String,
    val surnom: String = "",
    val adresse: String = "",
    val chef: String = "",
    val tel: String = "",
    val notes: String = "",
    val statut: String = STATUT_EN_COURS,
    /** Identifiants des matériels à livrer sur ce chantier. */
    val materiels: List<String> = emptyList(),
)

data class Fournisseur(
    val id: String,
    val nom: String,
    val adresse: String = "",
)

/** Matériel présent dans le camion (à charger ou chargé). */
data class Materiel(
    val id: String,
    val nom: String,
    val fournisseurId: String = "",
    val notes: String = "",
    val recupere: Boolean = false,
    /** Date de chargement au format jj/mm/aaaa, vide si pas chargé. */
    val dateRecupere: String = "",
    /** "", "cabine" ou "plateau". */
    val emplacement: String = "",
)

/** Matériel livré. Les noms sont figés au moment de la livraison. */
data class Archive(
    val id: String,
    val nom: String,
    val fournisseurNom: String = "",
    val chantiersNom: String = "",
    /** jj/mm/aaaa */
    val dateLivraison: String = "",
)

data class Palette(
    val id: String,
    val nom: String,
    val fournisseurId: String = "",
    val note: String = "",
)

/** Une journée de travail. La clé (date aaaa-mm-jj) est portée par la map. */
data class Heure(
    val debut: String = DEBUT_PAR_DEFAUT,
    val fin: String = "",
    val cp: Boolean = false,
    val note: String = "",
) {
    val remplie: Boolean get() = cp || fin.isNotBlank()

    companion object {
        const val DEBUT_PAR_DEFAUT = "06:30"
    }
}

const val ARRET_CHANTIER = "chantier"
const val ARRET_FOURNISSEUR = "fournisseur"
const val ARRET_LIBRE = "libre"

/**
 * Un arrêt du trajet. [ref] = id du chantier / fournisseur (vide pour une adresse libre).
 * Nom et adresse sont gardés en copie : l'arrêt reste valable même si la fiche est supprimée.
 */
data class ArretTrajet(
    val id: String,
    val type: String,
    val ref: String = "",
    val nom: String,
    val adresse: String,
)

data class AppData(
    val chantiers: List<Chantier> = emptyList(),
    val fournisseurs: List<Fournisseur> = emptyList(),
    val materiels: List<Materiel> = emptyList(),
    /** Plus récente en premier. */
    val archives: List<Archive> = emptyList(),
    val palettes: List<Palette> = emptyList(),
    /** Clé = date ISO aaaa-mm-jj. */
    val heures: Map<String, Heure> = emptyMap(),
    /** Trajet en cours, dans l'ordre. */
    val trajet: List<ArretTrajet> = emptyList(),
    /** Adresse de départ du trajet ; vide = ma position actuelle. */
    val trajetDepart: String = "",
)

enum class StatutEffectif { EN_LIVRAISON, EN_COURS, TERMINE }
