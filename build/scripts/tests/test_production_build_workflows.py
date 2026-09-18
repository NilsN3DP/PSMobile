from __future__ import annotations

import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]


class ProductionBuildWorkflowTests(unittest.TestCase):
    """Production helpers must not resolve a preview or ambiguous variant."""

    def make_build_apk_fixture(self, directory: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path]:
        root = directory / "repo"
        scripts = root / "build" / "scripts"
        scripts.mkdir(parents=True)
        for name in ("build-apk.sh", "env.sh"):
            source = ROOT / "build" / "scripts" / name
            target = scripts / name
            shutil.copy2(source, target)
            target.chmod(0o755)
        (root / "android/app/src/main/jniLibs").mkdir(parents=True)
        (root / "android/app/src/main/assets/psresources").mkdir(parents=True)

        tools = directory / "tools"
        tools.mkdir()
        docker = tools / "docker"
        docker.write_bytes(
            b"#!/usr/bin/env bash\n"
            b"if [ \"$1\" = image ]; then exit 0; fi\n"
            b"if [ \"$1\" = run ]; then printf '%s\\n' \"$@\" > \"$DOCKER_CAPTURE\"; exit 0; fi\n"
            b"exit 99\n"
        )
        docker.chmod(0o755)
        return root, tools

    @staticmethod
    def bash_path(path: pathlib.Path) -> str:
        rendered = str(path)
        if os.name != "nt":
            return rendered
        drive, tail = os.path.splitdrive(rendered)
        return f"/mnt/{drive[0].lower()}{tail.replace(chr(92), '/')}"

    def test_build_apk_resolves_explicit_production_gradle_tasks(self) -> None:
        """Changing a production task to an unqualified variant must fail this test."""
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=os.name == "nt") as temporary:
            root, tools = self.make_build_apk_fixture(pathlib.Path(temporary))
            capture = pathlib.Path(temporary) / "docker-command.txt"
            runner = pathlib.Path(temporary) / "run-build-apk.sh"
            runner.write_bytes(
                (
                    "#!/usr/bin/env bash\n"
                    f"export PATH='{self.bash_path(tools)}:/usr/bin:/bin'\n"
                    f"export DOCKER_CAPTURE='{self.bash_path(capture)}'\n"
                    f"exec '{self.bash_path(root / 'build/scripts/build-apk.sh')}' \"$@\"\n"
                ).encode("utf-8")
            )
            runner.chmod(0o755)

            for argument, expected_task in (
                ("debug", ":app:assembleProductionDebug"),
                ("release", ":app:assembleProductionRelease"),
                ("test", ":app:testProductionDebugUnitTest"),
            ):
                result = subprocess.run(
                    ["bash", self.bash_path(runner), argument],
                    cwd=root,
                    text=True,
                    capture_output=True,
                    check=False,
                    env=os.environ.copy(),
                )
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn(expected_task, capture.read_text(encoding="utf-8"))

    def test_tracked_build_apk_shell_syntax_is_portable(self) -> None:
        """A CRLF checkout must not make the production helper unparsable by Bash."""
        result = subprocess.run(
            ["bash", "-n", self.bash_path(ROOT / "build/scripts/build-apk.sh")],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    @unittest.skipUnless(os.name == "nt", "requires PowerShell and a .cmd adb fixture")
    def test_device_script_installs_only_the_production_debug_artifact(self) -> None:
        """A preview APK or legacy app-debug.apk path must not be installable here."""
        with tempfile.TemporaryDirectory() as temporary:
            fixture = pathlib.Path(temporary)
            project_root = fixture / "project[fixture]"
            production = project_root / "android/app/build/outputs/apk/production/debug"
            preview = project_root / "android/app/build/outputs/apk/preview/debug"
            production.mkdir(parents=True)
            preview.mkdir(parents=True)
            apk = production / "app-production-debug.apk"
            apk.write_bytes(b"production")
            (preview / "app-preview-debug.apk").write_bytes(b"preview")

            tools = fixture / "tools"
            tools.mkdir()
            trace = fixture / "adb-trace.txt"
            adb = tools / "adb.cmd"
            adb.write_text(
                "@echo off\r\n"
                "echo %*>>\"%ADB_TRACE%\"\r\n"
                "if \"%1\"==\"devices\" (echo List of devices attached&echo emulator-5554 device&exit /b 0)\r\n"
                "if \"%3\"==\"shell\" if \"%4\"==\"getprop\" (\r\n"
                "  if \"%5\"==\"ro.product.model\" echo Test Tablet\r\n"
                "  if \"%5\"==\"ro.build.version.release\" echo 15\r\n"
                "  if \"%5\"==\"ro.product.cpu.abi\" echo arm64-v8a\r\n"
                ")\r\n",
                encoding="utf-8",
            )
            environment = os.environ.copy()
            environment["PATH"] = f"{tools}{os.pathsep}{environment['PATH']}"
            environment["ADB_TRACE"] = str(trace)

            result = subprocess.run(
                [
                    "pwsh",
                    "-NoProfile",
                    "-File",
                    ROOT / "build/scripts/geraetetest.ps1",
                    "-Projektwurzel",
                    project_root,
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
                env=environment,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            installed = trace.read_text(encoding="utf-8")
            self.assertIn(str(apk), installed)
            self.assertNotIn("preview", installed.lower())


if __name__ == "__main__":
    unittest.main()
