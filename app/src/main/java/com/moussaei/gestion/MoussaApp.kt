package com.moussaei.gestion

import android.app.Application
import com.moussaei.gestion.data.Repository
import com.moussaei.gestion.rappel.Rappel

open class MoussaApp : Application() {

    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(this)
        repo.sauvegardeDuJour()
        Rappel.creerCanal(this)
        Rappel.programmerSiActif(this)
    }

    companion object {
        lateinit var instance: MoussaApp
            private set
    }
}
