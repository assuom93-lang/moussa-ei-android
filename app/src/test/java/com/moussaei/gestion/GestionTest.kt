package com.moussaei.gestion

import com.moussaei.gestion.core.Reglages
import com.moussaei.gestion.core.Coord
import com.moussaei.gestion.core.Trajets
import com.moussaei.gestion.core.ARRET_LIBRE
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
    fun trajet() {
        val (d0, imp) = base()
        var d = Gestion.ajouterChantierAuTrajet(d0, d0.chantiers[0]).first
        assertEquals(Gestion.AjoutTrajet.DEJA, Gestion.ajouterChantierAuTrajet(d, d.chantiers[0]).second)
        d = Gestion.ajouterAuTrajet(d, ARRET_LIBRE, "", "", "5 rue Libre").first
        d = Gestion.deplacerArret(d, d.trajet[1].id, -1)
        assertEquals(ARRET_LIBRE, d.trajet[0].type)
        // Départ = ma position : pas d'origine dans le lien.
        assertFalse(Gestion.liensGoogleMaps(d.trajet, "")[0].url.contains("origin="))
        assertTrue(Gestion.liensGoogleMaps(d.trajet, "Dépôt")[0].url.contains("origin="))
        val d2 = Gestion.changerDepart(d, "Dépôt")
        assertEquals(d2, assertNotNullAndGet(Csv.import(Csv.export(d2))).data)
        assertEquals(2, Gestion.importer(d2, imp).trajet.size)
        // 12 arrêts : 2 parties (10 + 2)
        var big = d0
        repeat(12) { i -> big = Gestion.ajouterAuTrajet(big, ARRET_LIBRE, "", "", "Adresse $i").first }
        assertEquals(listOf(1 to 10, 11 to 12), Gestion.liensGoogleMaps(big.trajet, "").map { it.premier to it.dernier })
        // Recherche de palette : renvoie l'adresse du fournisseur.
        val r = Gestion.rechercher(d0, "verte")
        assertEquals(1, r.size)
        assertEquals("76 Rue Test, 93700 Drancy", r[0].adresse)
    }

    @Test
    fun enseignesEtPalettesARendre() {
        val (d0, _) = base()
        assertEquals("Point P", Gestion.devinerEnseigne("Points P (P.P) Aubervilliers"))
        assertEquals("Loxam", Gestion.devinerEnseigne("Loxam stade de France "))
        assertEquals("", Gestion.devinerEnseigne("Dépôt "))
        val d = d0.copy(fournisseurs = d0.fournisseurs + com.moussaei.gestion.core.Fournisseur("_pp1", "Points P (P.P) Aubervilliers", "Face au, 29 Quai Lucien Lefranc, 93300 Aubervilliers"))
        val cat = Gestion.lireCatalogue("enseigne,nom,adresse\nPoint P,Point P Aubervilliers,\"29 Quai Lucien Lefranc, 93300 Aubervilliers\"\nPoint P,Point P Bobigny,\"1 rue Test, 93000 Bobigny\"\nBigmat,Bigmat,\"1 rue X, 93000 Bobigny\"\n")
        val m = Gestion.migrer(d, cat)
        assertEquals(1, Gestion.agencesDe(m, "Point P").size) // v4.3 : rien n'est ajouté aux fournisseurs
        assertEquals(listOf("Point P Bobigny"), Gestion.lexique(m, cat).map { it.nom }) // doublon Aubervilliers évité, Bigmat ignoré
        assertEquals("76 Rue Test, 93700 Drancy", m.reglages.depot) // fournisseur « Dépôt »
        assertEquals(m, Gestion.migrer(m, cat))
        var r = Gestion.recupererPalettes(m, "Point P", "SLM", 2, "", LocalDate.of(2026, 11, 2))
        r = Gestion.recupererPalettes(r, "point p", "", 1, "", LocalDate.of(2026, 11, 2))
        assertEquals(3, Gestion.nbARendre(r))
        assertEquals(1, Gestion.aRendreParEnseigne(r).size)
        r = Gestion.marquerRendue(r, r.aRendre.first().id, LocalDate.of(2026, 11, 3))
        assertEquals(2, Gestion.nbARendre(r))
        assertEquals(r, assertNotNullAndGet(Csv.import(Csv.export(r))).data)
        // Le trajet finit au dépôt.
        val t = Gestion.ajouterAuTrajet(r, ARRET_LIBRE, "", "", "5 rue Libre").first
        assertTrue(Gestion.liensGoogleMaps(t.trajet, "", Gestion.arrivee(t))[0].url.contains("destination=76+Rue+Test"))
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
        // Pas de week-end : vendredi → lundi, samedi → vendredi.
        assertEquals(lundi, Heures.decalerOuvre(LocalDate.of(2026, 10, 30), 1))
        assertEquals(LocalDate.of(2026, 10, 30), Heures.decalerOuvre(lundi, -1))
        assertEquals(LocalDate.of(2026, 10, 30), Heures.jourOuvre(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun trajetsEnregistres() {
        val jour = LocalDate.of(2026, 11, 2)
        var d = AppData(reglages = Reglages(depot = "76 Rue Test, 93700 Drancy", finirAuDepot = true))
        d = Gestion.ajouterAuTrajet(d, ARRET_LIBRE, "", "Client", "5 rue Libre, Paris").first
        val (d1, id) = Trajets.enregistrer(d, jour, "07:10")
        assertEquals("Tournée du lundi 2 novembre", Trajets.parId(d1, id)!!.nom)
        // Même trajet le même jour : pas de doublon.
        val (d2, id2) = Trajets.enregistrer(d1, jour, "09:00")
        assertEquals(id, id2)
        assertEquals(1, d2.trajetsEnregistres.size)
        // Départ « ma position » : le calcul part du dépôt et revient au dépôt.
        assertEquals(listOf("76 Rue Test, 93700 Drancy", "5 rue Libre, Paris", "76 Rue Test, 93700 Drancy"), Trajets.adressesCalcul(d2, Trajets.parId(d2, id)!!))
        var d3 = Trajets.enregistrerCalcul(d2, id, 42_000, 2 * 3600, "_p~iF~ps|U_ulLnnqC", listOf(3600, 3600))
        d3 = Trajets.memoriserGeocode(d3, "5 rue Libre, Paris", Coord(48.85, 2.35))
        assertEquals(120, Trajets.conduite(d3, jour))
        d3 = Gestion.enregistrerHeure(d3, jour, Heure("06:30", "16:00", conduiteMin = 150))
        assertEquals(150, Trajets.conduite(d3, jour))
        assertEquals(d3, assertNotNullAndGet(Csv.import(Csv.export(d3))).data)
        assertEquals(3, Trajets.decoderPolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@").size)
        // Modifier les arrêts efface le calcul.
        val d4 = Trajets.ajouterArret(d3, id, ARRET_LIBRE, "", "", "8 rue Autre")
        assertFalse(Trajets.parId(d4, id)!!.calcule)
        // Rappels de conduite.
        val d5 = Gestion.enregistrerHeure(d3, jour, Heure("06:30", "18:00", conduiteMin = 10 * 60 + 30))
        assertTrue(Trajets.alertes(d5, jour).any { it.niveau == Trajets.Niveau.DEPASSEMENT })
        assertEquals("1h35", Heures.formatMinutes(95))
    }
}
