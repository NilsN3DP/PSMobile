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
        versionCode = 1
        versionName = "0.1.0-m3"

        ndk {
            // arm64 ist das Hauptziel, x86_64 nur fuer den Emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Die native Bibliothek wird NICHT von Gradle gebaut, sondern von
    // build/scripts/build-core.sh im Docker-Container auf dem Unraid und
    // dann hierher kopiert. Gradle packt sie nur ein.
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isJniDebuggable = true
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)

    debugImplementation(libs.androidx.ui.tooling)
}
