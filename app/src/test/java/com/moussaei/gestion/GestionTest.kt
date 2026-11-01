package com.moussaei.gestion

import com.moussaei.gestion.core.AppData
import com.moussaei.gestion.core.Csv
import com.moussaei.gestion.core.EMPLACEMENT_CABINE
import com.moussaei.gestion.core.Gestion
import com.moussaei.gestion.core.Heure
import com.moussaei.gestion.core.Heures
import com.moussaei.gestion.core.StatutEffectif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GestionTest {

    // Extrait au format exact d'un export v3.21 (données fictives).
    private val exportV321 = listOf(
        "## CHANTIERS",
        "id,nom,surnom,adresse,chef,tel,notes,statut,materiels",
        "_aaaaaaaa1,Chantier A ,Surnom A ,\"1 Rue Exemple, 75001 Paris\",Chef A ,06 00 00 00 01,,en_cours,",
        "_aaaaaaaa2,Chantier B,,10 avenue Test,Chef B,0600000002,\"Code porte \"\"B846\"\"\",en_cours,",
        "_aaaaaaaa3,Chantier C,,\"48°51'33.3\"\"N 2°20'16.7\"\"E\",Chef C,07,,termine,",
        "",
        "## FOURNISSEURS",
        "id,nom,adresse",
        "_fffffff01,Dépôt ,\"76 Rue Test, 93700 Drancy\"",
        "_fffffff02,Fournisseur 2,Ville",
        "",
        "## ARCHIVES",
        "id,nom,fournisseur,chantier,dateLivraison",
        "_arch00001,\"Étais 1,70 x20\",Inconnu,Chantier A,16/03/2026",
        "",
        "## PALETTES",
        "id,nom,fournisseurId,note",
        "_pal000001,SLM,_fffffff02,",
        "_pal000002,Palette verte,_fffffff01,Angle vert ou côté",
        "",
        "## HEURES",
        "date,debut,fin,cp,note",
    ).joinToString("\n")

    private fun base(): Pair<AppData, Csv.Import> {
        val imp = assertNotNullAndGet(Csv.import(exportV321))
        return Gestion.importer(AppData(), imp) to imp
    }

    private fun <T> assertNotNullAndGet(v: T?): T { assertNotNull(v); return v!! }

    @Test
    fun importV321() {
        val (d, imp) = base()
        assertFalse(imp.contientMateriels)
        assertEquals(3, d.chantiers.size)
        assertEquals(2, d.fournisseurs.size)
        assertEquals(1, d.archives.size)
        assertEquals(2, d.palettes.size)
        assertEquals("Code porte \"B846\"", d.chantiers[1].notes)
        assertEquals("Étais 1,70 x20", d.archives[0].nom)
        assertEquals("Chantier A", d.chantiers[0].nom)
    }

    @Test
    fun allerRetourAvecNotesMultilignes() {
        val (d0, _) = base()
        val d = d0.copy(chantiers = d0.chantiers.map { it.copy(notes = "Ligne 1\nLigne 2, avec virgule") })
        val again = assertNotNullAndGet(Csv.import(Csv.export(d)))
        assertTrue(again.contientMateriels)
        assertEquals(d, again.data)
    }

    @Test
    fun camionEtLivraison() {
        val (d0, imp) = base()
        val today = LocalDate.of(2026, 11, 2)
        val (d1, n) = Gestion.ajouterListe(d0, "Ba 13 x10\n\n Sac ciment x5 \nRubalise", "_fffffff01", "_aaaaaaaa2")
        assertEquals(3, n)
        assertEquals(StatutEffectif.EN_LIVRAISON, Gestion.statut(d1, d1.chantiers[1]))
        assertEquals("_aaaaaaaa2", Gestion.chantiersTries(d1).first().id)
        assertEquals("_aaaaaaaa3", Gestion.chantiersTries(d1).last().id) // terminé à la fin

        val id = d1.materiels[1].id
        var d2 = Gestion.basculerCharge(d1, id, today)
        assertEquals("02/11/2026", d2.materiels[1].dateRecupere)
        assertEquals(2, Gestion.nbACharger(d2))
        d2 = Gestion.basculerEmplacement(d2, id, EMPLACEMENT_CABINE)
        assertEquals(EMPLACEMENT_CABINE, d2.materiels[1].emplacement)
        d2 = Gestion.livrer(d2, id, today)
        assertEquals(2, d2.materiels.size)
        assertEquals("Sac ciment x5", d2.archives.first().nom)
        assertEquals("Dépôt", d2.archives.first().fournisseurNom)
        assertEquals("Chantier B", d2.archives.first().chantiersNom)
        assertTrue(d2.chantiers.none { id in it.materiels })

        // Un import v3.21 (sans camion) garde le camion actuel.
        assertEquals(2, Gestion.importer(d2, imp).materiels.size)
    }

    @Test
    fun heures() {
        assertEquals(8.5, Heures.total(Heure("06:30", "16:00"))!!, 0.001)
        assertEquals("8h45", Heures.format(Heures.total(Heure("07:10", "16:55"))))
        assertEquals("—", Heures.format(null))
        val lundi = LocalDate.of(2026, 11, 2)
        val hs = mapOf("2026-10-28" to Heure("06:30", "16:00"))
        assertEquals(LocalDate.of(2026, 10, 29), Heures.dernierJourARemplir(hs, lundi))
        assertEquals("Lundi 2 novembre", Heures.titreJour(lundi))
    }
}
