plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/*
 * Gradle kann seine inkrementellen Ausgaben auf manchen SMB-Freigaben
 * nicht atomar ersetzen. Fuer lokale Windows-Pruefungen darf der
 * Buildordner deshalb auf eine lokale Platte zeigen; CI und der
 * Docker-Build bleiben ohne die Variable unveraendert.
 */
System.getenv("PSM_LOCAL_BUILD_ROOT")?.takeIf { it.isNotBlank() }?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
