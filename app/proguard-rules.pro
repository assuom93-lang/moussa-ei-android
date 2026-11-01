# moussa EI : pas de réflexion dans le code de l'appli ; les bibliothèques (Compose,
# WorkManager, ML Kit) fournissent leurs propres règles.
-keep class com.moussaei.gestion.rappel.RappelWorker { *; }
