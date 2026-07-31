# Android-Profilupdate veröffentlichen

Der APK-Build enthält nur die erlaubte Manifest-URL und deren HTTPS-Host.
Das Profilpaket selbst ist statisch und wird vor dem Aktivieren in der App
gegen SHA-256, Vollständigkeit und die minimale Slicer-Version geprüft.

1. Ressourcen und INDX-Profile aktualisieren.
2. Aus dem Projektstamm ein Paket erzeugen:

   ```powershell
   ./build/scripts/package-profile-update.ps1 `
     -Version 2.5.6 `
     -PackageUrl https://updates.example.org/psmobile/2.5.6.zip `
     -OutputDirectory ./release/profiles `
     -ReleaseNote 'CORE One INDX 4T/8T' -ReleaseNote 'Aktualisierte Filamentprofile'
   ```

3. Die erzeugte ZIP an exakt `package_url` und `manifest.json` an die
   konfigurierte Manifest-URL hochladen. Keine ZIP nach dem Hashen ändern.
4. Den Produktionsbuild mit beiden Gradle-Properties erzeugen:

   ```text
   -PprofileManifestUrl=https://updates.example.org/psmobile/manifest.json
   -PprofileUpdateAllowedHosts=updates.example.org
   ```

Ein leerer Wert deaktiviert den Netzwerkcheck absichtlich. Dadurch wird nie
ein fremder oder unbestätigter Updatehost ausgeliefert.
