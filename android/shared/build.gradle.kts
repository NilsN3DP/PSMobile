/*
 * Gemeinsame Regeln fuer Android und iOS - siehe docs/entscheidungen.md,
 * E-13.
 *
 * Hier steht nur, was keinen Bildschirmbezug hat: Schwellwerte,
 * Zuordnungen, Entscheidungen. Kein Compose, kein SwiftUI, keine
 * Plattform-APIs. Was hier liegt, gilt fuer beide Apps und wird nur
 * einmal geprueft.
 *
 * Die iOS-Fassung baut Kotlin/Native als Framework - das geht nur auf
 * einem Mac. Auf Linux uebersetzt nur das Android-Ziel; das ist gewollt
 * und kein Fehler.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    // ColorMixCodec liest und schreibt JSON. Weitere Regeln werden
    // folgen - die Profile und tabs.json sind auch JSON.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    // Geraet und Simulator getrennt: ein Simulator-Binary laeuft nicht auf
    // dem iPad und umgekehrt.
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "PSMShared"
            // Statisch: die App bindet es direkt ein, wie schon den Kern.
            // Ein dynamisches Framework muesste mitsigniert werden, ohne
            // etwas dafuer zurueckzugeben.
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies { implementation(libs.kotlinx.serialization.json) }
        }
        val commonTest by getting {
            dependencies { implementation(libs.kotlin.test) }
        }
    }
}

android {
    namespace = "de.psmobile.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
