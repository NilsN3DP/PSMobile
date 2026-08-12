plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.psmobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.psmobile"
        // minSdk 26 deckt 2026 praktisch alle Geraete ab und erspart uns
        // die Sonderfaelle von std::filesystem und Foreground-Services.
        minSdk = 26
        targetSdk = 35
        // Eine Stelle fuer die Version, von aussen ueberschreibbar:
        //   ./gradlew :app:assembleProductionRelease -PpsmVersionCode=7
        //
        // versionName ist gleich mit CFBundleShortVersionString in
        // ios/PSMobile/Support/Info.plist - beide Apps sollen sich als
        // dieselbe Version melden. Vorher stand hier "0.1.0-m3" gegen
        // "0.1.0" auf iOS.
        //
        // versionCode MUSS bei jedem Build steigen, der das Haus
        // verlaesst. Android verweigert die Installation eines APK ueber
        // eine vorhandene App mit gleicher oder hoeherer Nummer - die
        // dauerhafte 1 haette jedes Beta-Update blockiert.
        versionCode = providers.gradleProperty("psmVersionCode").getOrElse("2").toInt()
        versionName = providers.gradleProperty("psmVersionName").getOrElse("0.1.0")

        val manifestUrl = providers.gradleProperty("profileManifestUrl").orNull.orEmpty()
        val allowedHosts = providers.gradleProperty("profileUpdateAllowedHosts").orNull.orEmpty()
        buildConfigField("String", "PROFILE_UPDATE_MANIFEST_URL", "\"$manifestUrl\"")
        buildConfigField("String", "PROFILE_UPDATE_ALLOWED_HOSTS", "\"$allowedHosts\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64 ist das Hauptziel, x86_64 nur fuer den Emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Die native Bibliothek wird NICHT von Gradle gebaut, sondern von
    // build/scripts/build-core.sh im Docker-Container auf dem Unraid und
    // dann hierher kopiert. Gradle packt sie nur ein.
    // Die zuvor auf dem Share erzeugten Dateien unter jniLibs sind
    // absichtlich unangetastet: ihre Unix-Besitzrechte verhindern ein
    // sicheres Ersetzen von Windows aus. Alle aktuellen und künftigen
    // Stage-Läufe verwenden deshalb ausschließlich dieses Verzeichnis.
    sourceSets["main"].jniLibs.setSrcDirs(listOf("src/main/jniLibsFixed"))

    buildTypes {
        release {
            // R8 an. proguard-rules.pro war hier schon eingetragen, die
            // Datei gab es aber gar nicht - mit isMinifyEnabled = false
            // hat das nie jemand gemerkt. Was gehalten werden muss und
            // warum, steht jetzt dort; entscheidend ist die JNI-Bruecke,
            // die allein ueber Symbolnamen funktioniert.
            isMinifyEnabled = true
            // Ungenutzte Bilder und Layouts fallen mit weg. Gefahrlos,
            // weil die App keine Ressource ueber getIdentifier() sucht
            // und die PrusaSlicer-Daten in assets/ liegen, nicht in res/.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isJniDebuggable = true
        }
    }

    // Getrennte APKs je Architektur.
    //
    // Bisher steckten arm64 und x86_64 in derselben Datei: rund 21 MB
    // Emulator-Bibliotheken in jedem APK, das an Testerinnen und Tester
    // geht, obwohl kein Telefon sie ausfuehren kann.
    //
    // Das universelle APK bleibt zusaetzlich erhalten - eine Datei, die
    // auf Geraet und Emulator laeuft, ist beim Entwickeln zu praktisch,
    // um sie aufzugeben. Zum Verteilen nimmt man die arm64-Fassung.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("production") {
            dimension = "distribution"
        }
        create("preview") {
            dimension = "distribution"
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "PSMobile Preview")
        }
    }

    packaging {
        jniLibs {
            // Die .so wird bereits vom NDK-Build gestrippt und muss
            // unkomprimiert bleiben, damit sie direkt aus dem APK
            // gemappt werden kann (spart RAM beim Start).
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Die gemeinsamen Regeln (E-13). Was hier drin steht, gilt auch fuer
    // die iOS-App - Aenderungen wirken auf beiden Seiten.
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)

    // Kamera-Scanner fuer die experimentelle lokale PrusaLink-QR-Kopplung.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Oberflaechentests. Bis hierher gab es unter app/src/test nur
    // reine Logiktests - kein einziger Test hat je einen Bildschirm
    // gezeichnet, waehrend iOS 26 XCUITest-Dateien hat. Genau deshalb
    // sind der Dichtefehler in den Popups und die unuebersetzten
    // Beschriftungen so lange unentdeckt geblieben.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
