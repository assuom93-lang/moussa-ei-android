package com.moussaei.gestion

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Appli de test : dépose des données au format v4.1 (sans réglages) avant le démarrage. */
class AppDeTest : MoussaApp() {
    override fun onCreate() {
        File(filesDir, "donnees.csv").writeText(DONNEES_V41)
        super.onCreate()
    }

    companion object {
        val DONNEES_V41 = listOf(
            "## CHANTIERS",
            "id,nom,surnom,adresse,chef,tel,notes,statut,materiels",
            "_c00000001,Chantier A,Surnom,\"1 Rue Exemple, 75001 Paris\",Chef A,0600000001,\"Note sur\ndeux lignes\",en_cours,_m00000001",
            "_c00000002,Chantier B,,\"2 Rue Exemple, 93200 Saint-Denis\",Chef B,0600000002,,termine,",
            "",
            "## FOURNISSEURS",
            "id,nom,adresse",
            "_f00000001,Points P (P.P) Aubervilliers,\"Face au, 29 Quai Lucien Lefranc, 93300 Aubervilliers\"",
            "_f00000002,Raboni,Courneuve",
            "_f00000003,Loxam stade de France ,Aubervilliers",
            "_f00000004,Dépôt ,\"76 Rue Test, 93700 Drancy\"",
            "_f00000005,Kiloutou Saint-Denis (Stade de France),41 Boulevard Anatole France",
            "",
            "## ARCHIVES",
            "id,nom,fournisseur,chantier,dateLivraison",
            "_a00000001,Chaise,Dépôt,Chantier A,07/05/2026",
            "",
            "## PALETTES",
            "id,nom,fournisseurId,note",
            "_p00000001,SLM,_f00000002,",
            "_p00000002,Palette verte,_f00000001,Angle vert",
            "",
            "## HEURES",
            "date,debut,fin,cp,note",
            "2026-10-30,06:30,16:00,0,",
            "",
            "## MATERIELS",
            "id,nom,fournisseurId,notes,recupere,dateRecupere,emplacement",
            "_m00000001,Rubalise,_f00000004,,0,,",
            "",
            "## TRAJET",
            "id,type,ref,nom,adresse",
            "_t00000001,chantier,_c00000001,Chantier A,\"1 Rue Exemple, 75001 Paris\"",
        ).joinToString("\n")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AppDeTest::class, qualifiers = "w400dp-h900dp-xxhdpi")
class DemarrageTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun ouvrirTuile(titre: String) {
        compose.onAllNodesWithText(titre)[0].performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun retour() {
        compose.onNodeWithText("←").performClick()
        compose.waitForIdle()
    }

    @Test
    fun chaqueEcranSOuvreSansPlanter() {
        compose.onNodeWithText("moussa EI").assertExists()
        // La migration v4.2 a tourné : enseignes + agences du catalogue.
        val d = MoussaApp.instance.repo.donnees
        check(d.reglages.version >= com.moussaei.gestion.core.VERSION_DONNEES) { "migration non faite" }
        check(d.fournisseurs.size > 100) { "agences non ajoutées : ${d.fournisseurs.size}" }
        check(d.reglages.depot == "76 Rue Test, 93700 Drancy") { "dépôt : ${d.reglages.depot}" }

        for (t in listOf("Trajet", "Chantiers", "Fournisseurs", "Palettes", "Camion", "Heures", "Historique")) {
            ouvrirTuile(t)
            retour()
        }
        // Réglages
        compose.onNodeWithText("⚙️").performClick(); compose.waitForIdle(); retour()

        // Fournisseurs → enseigne Point P → liste des agences
        ouvrirTuile("Fournisseurs")
        compose.onAllNodesWithText("🏬 Point P")[0].performScrollTo().performClick(); compose.waitForIdle()
        compose.onAllNodesWithText("✕")[0].performClick(); compose.waitForIdle()
        retour()

        // Palettes → « J'ai récupéré des palettes »
        ouvrirTuile("Palettes")
        compose.onNodeWithText("➕ J'ai récupéré des palettes").performScrollTo().performClick(); compose.waitForIdle()
        compose.onAllNodesWithText("✕")[0].performClick(); compose.waitForIdle()
        retour()

        // Trajet : chantier déjà dans le trajet, retour au dépôt
        ouvrirTuile("Trajet")
        compose.onAllNodesWithText("🏁 Retour au dépôt")[0].assertExists()
        retour()
    }
}
