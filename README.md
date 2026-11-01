# moussa EI (application Android native)

Gestion des chantiers, du camion, des fournisseurs, des palettes, des heures et des livraisons.
Réécriture native (Kotlin / Jetpack Compose) de l'ancienne appli WebView v3.21.

## Installer
Chaque envoi sur `main` compile l'APK sur GitHub Actions et le publie dans **Releases**.
Lien direct vers la dernière version : `releases/latest/download/moussa-EI.apk`.

## Données
- Stockées sur le téléphone dans `files/donnees.csv` (même format que le backup).
- Copie automatique de chaque jour (7 derniers jours) dans `files/sauvegardes`.
- Sauvegarde automatique Android vers le compte Google (Drive).
- Import du backup de l'ancienne appli : Réglages → Importer (fichier ou texte collé).

## Structure
- `core/` : modèle, format CSV, règles de gestion et heures (Kotlin pur, testé).
- `data/` : stockage.
- `ui/` : écrans Compose.
- `rappel/` : notification de 15h (WorkManager).
