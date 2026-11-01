plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.moussaei.gestion"
    compileSdk = 36

    defaultConfig {
        // Identifiant différent de l'ancienne appli (com.moussaei.app) : les deux peuvent
        // cohabiter le temps de transférer les données.
        applicationId = "com.moussaei.gestion"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "4.3"
        // Seulement les processeurs des téléphones actuels (allège l'APK du scanner de texte).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Clé de signature fixe : fournie par GitHub Actions depuis les secrets du dépôt
    // (MOUSSA_KEYSTORE_B64 / MOUSSA_KEY_PASSWORD), jamais stockée dans le code public.
    // Chaque nouvelle version s'installe ainsi par-dessus l'ancienne sans perdre les données.
    val cle = rootProject.file("signing/moussa-ei.jks")
    val motDePasse = System.getenv("MOUSSA_KEY_PASSWORD") ?: ""
    signingConfigs {
        create("moussa") {
            storeFile = cle
            storeType = "pkcs12"
            storePassword = motDePasse
            keyAlias = "moussaei"
            keyPassword = motDePasse
        }
    }

    buildTypes {
        release {
            // R8 désactivé (v4.2.1) : l'appli se fermait au démarrage avec le code compressé.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (cle.exists()) signingConfig = signingConfigs.getByName("moussa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // Rappel des heures à 15h (fonctionne appli fermée)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Scanner de texte : reconnaissance SUR LE TÉLÉPHONE (modèle inclus, marche hors connexion)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
    // Test de démarrage : lance la vraie appli (Robolectric) et ouvre chaque écran.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
