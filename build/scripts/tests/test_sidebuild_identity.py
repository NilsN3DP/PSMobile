from __future__ import annotations

import os
import pathlib
import plistlib
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
ANDROID = ROOT / "android"
IOS = ROOT / "ios"
JAVA_17 = pathlib.Path(r"C:\Program Files\Android\Android Studio\jbr")


class SideBuildIdentityTests(unittest.TestCase):
    """Verify installable artifacts, not source-text declarations."""

    def android_environment(self) -> dict[str, str]:
        environment = os.environ.copy()
        if JAVA_17.is_dir():
            environment["JAVA_HOME"] = str(JAVA_17)
        return environment

    def aapt2(self) -> pathlib.Path:
        sdk = pathlib.Path(
            os.environ.get("ANDROID_HOME")
            or os.environ.get("ANDROID_SDK_ROOT")
            or pathlib.Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk"
        )
        candidates = sorted(sdk.glob("build-tools/*/aapt2.exe"), reverse=True)
        if not candidates:
            candidates = sorted(sdk.glob("build-tools/*/aapt2"), reverse=True)
        self.assertTrue(candidates, "Android SDK aapt2 was not found")
        return candidates[0]

    def apk_badging(self, apk: pathlib.Path) -> str:
        result = subprocess.run(
            [self.aapt2(), "dump", "badging", apk],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_android_artifacts_are_parallel_and_isolated(self) -> None:
        gradle = ANDROID / ("gradlew.bat" if os.name == "nt" else "gradlew")
        result = subprocess.run(
            [
                gradle,
                "-p",
                str(ANDROID),
                ":app:assembleProductionDebug",
                ":app:assemblePreviewDebug",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
            env=self.android_environment(),
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

        production = self.apk_badging(
            ANDROID / "app/build/outputs/apk/production/debug/app-production-debug.apk"
        )
        preview = self.apk_badging(
            ANDROID / "app/build/outputs/apk/preview/debug/app-preview-debug.apk"
        )
        self.assertIn("package: name='de.psmobile'", production)
        self.assertIn("application-label:'PSMobile'", production)
        self.assertIn("package: name='de.psmobile.preview'", preview)
        self.assertIn("application-label:'PSMobile Preview'", preview)
        self.assertNotEqual(production, preview)

        for artifact in (production, preview):
            self.assertNotIn("sharedUserId=", artifact)

    @unittest.skipUnless(sys.platform == "darwin", "requires the Mac Xcode host")
    def test_ios_artifacts_are_parallel_and_isolated(self) -> None:
        generated = subprocess.run(
            ["xcodegen", "generate"],
            cwd=IOS,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(generated.returncode, 0, generated.stdout + generated.stderr)

        with tempfile.TemporaryDirectory() as temporary:
            derived = pathlib.Path(temporary) / "derived"
            products: dict[str, pathlib.Path] = {}
            for scheme, bundle_id, display_name in (
                ("PSMobile", "de.psmobile", "PSMobile"),
                ("PSMobilePreview", "de.psmobile.preview", "PSMobile Preview"),
            ):
                settings = subprocess.run(
                    [
                        "xcodebuild",
                        "-project",
                        "PSMobile.xcodeproj",
                        "-scheme",
                        scheme,
                        "-configuration",
                        "Debug",
                        "-showBuildSettings",
                    ],
                    cwd=IOS,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(settings.returncode, 0, settings.stdout + settings.stderr)
                self.assertIn(f"PRODUCT_BUNDLE_IDENTIFIER = {bundle_id}", settings.stdout)

                build = subprocess.run(
                    [
                        "xcodebuild",
                        "-project",
                        "PSMobile.xcodeproj",
                        "-scheme",
                        scheme,
                        "-sdk",
                        "iphonesimulator",
                        "-configuration",
                        "Debug",
                        "-derivedDataPath",
                        str(derived),
                        "build",
                    ],
                    cwd=IOS,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
                app = derived / "Build/Products/Debug-iphonesimulator" / f"{scheme}.app"
                products[scheme] = app
                with (app / "Info.plist").open("rb") as stream:
                    info = plistlib.load(stream)
                self.assertEqual(info["CFBundleIdentifier"], bundle_id)
                self.assertEqual(info["CFBundleDisplayName"], display_name)

                entitlements = subprocess.run(
                    ["codesign", "-d", "--entitlements", ":-", app],
                    cwd=IOS,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(entitlements.returncode, 0, entitlements.stdout + entitlements.stderr)
                combined = entitlements.stdout + entitlements.stderr
                self.assertNotIn("com.apple.security.application-groups", combined)
                self.assertNotIn("keychain-access-groups", combined)

            self.assertNotEqual(products["PSMobile"], products["PSMobilePreview"])


if __name__ == "__main__":
    unittest.main()
