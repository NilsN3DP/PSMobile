from __future__ import annotations

import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest


SOURCE_ROOT = pathlib.Path(__file__).resolve().parents[3]


class PackageIpaTests(unittest.TestCase):
    def make_fixture(self, directory: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path]:
        root = directory / "repo"
        scripts = root / "build" / "scripts"
        scripts.mkdir(parents=True)
        package_script = scripts / "package-ipa.sh"
        shutil.copy2(SOURCE_ROOT / "build" / "scripts" / "package-ipa.sh", package_script)
        package_script.write_bytes(package_script.read_bytes().replace(b"\r\n", b"\n"))
        for name in ("build-shared-ios.sh", "stage-resources.sh"):
            path = scripts / name
            path.write_bytes(b"#!/usr/bin/env bash\nexit 0\n")
            path.chmod(0o755)

        (root / "ios").mkdir()
        tools = root / "build-out" / "mac-tools" / "bin"
        tools.mkdir(parents=True)
        self.write_tool(tools / "xcodegen", "#!/usr/bin/env bash\nexit 0\n")
        self.write_tool(
            tools / "codesign",
            "#!/usr/bin/env bash\nfor target in \"$@\"; do :; done\nprintf signed > \"$target/.signed\"\n",
        )
        self.write_tool(
            tools / "zip",
            "#!/usr/bin/env bash\ntarget=\"$2\"\nprintf 'fresh ipa\\n' > \"$target\"\n",
        )
        self.write_tool(
            tools / "unzip",
            "#!/usr/bin/env bash\ntest \"$1\" = -t\ntest -s \"$2\"\n",
        )
        runner = root / "run-package-ipa.sh"
        runner.write_bytes(
            b'#!/usr/bin/env bash\n'
            b'export PATH="$(dirname "$0")/build-out/mac-tools/bin:/usr/bin:/bin"\n'
            b'exec "$(dirname "$0")/build/scripts/package-ipa.sh" "$@"\n'
        )
        runner.chmod(0o755)
        return root, tools

    @staticmethod
    def write_tool(path: pathlib.Path, content: str) -> None:
        path.write_bytes(content.encode("utf-8"))
        path.chmod(0o755)

    @staticmethod
    def bash_path(path: pathlib.Path) -> str:
        rendered = str(path)
        if os.name != "nt":
            return rendered
        drive, tail = os.path.splitdrive(rendered)
        return f"/mnt/{drive[0].lower()}{tail.replace(chr(92), '/')}"

    def run_package(
        self, root: pathlib.Path, output: pathlib.Path, mode: str
    ) -> subprocess.CompletedProcess[str]:
        tools = root / "build-out" / "mac-tools" / "bin"
        self.write_tool(
            tools / "xcodebuild",
            "#!/usr/bin/env bash\n"
            f"if [ \"{mode}\" = fail ]; then\n"
            "  echo 'ld: Undefined symbols for architecture arm64'\n"
            "  echo '  \"_missing_symbol\", referenced from:'\n"
            "  echo 'clang: error: linker command failed with exit code 1'\n"
            "  exit 65\n"
            "fi\n"
            "while [ \"$#\" -gt 0 ]; do\n"
            "  if [ \"$1\" = -derivedDataPath ]; then derived=\"$2\"; shift 2; continue; fi\n"
            "  shift\n"
            "done\n"
            "app=\"${derived}/Build/Products/Debug-iphoneos/PSMobile.app\"\n"
            "mkdir -p \"$app\"\n"
            "printf fresh > \"$app/PSMobile\"\n"
            "echo BUILD SUCCEEDED\n",
        )
        environment = os.environ.copy()
        return subprocess.run(
            [
                "bash",
                self.bash_path(root / "run-package-ipa.sh"),
                self.bash_path(output),
            ],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
            env=environment,
        )

    def test_failed_xcodebuild_keeps_an_old_app_and_ipa_out_of_the_release_path(self) -> None:
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=os.name == "nt") as temporary:
            root, _ = self.make_fixture(pathlib.Path(temporary))
            stale_app = (
                root
                / "build-out/ios-derived-device/Build/Products/Debug-iphoneos/PSMobile.app"
            )
            stale_app.mkdir(parents=True)
            (stale_app / "PSMobile").write_text("stale", encoding="utf-8")
            output = root / "release" / "PSMobile.ipa"
            output.parent.mkdir()
            output.write_text("old ipa", encoding="utf-8")

            result = self.run_package(root, output, "fail")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("linker command failed", result.stdout)
            self.assertFalse(stale_app.exists())
            self.assertEqual(output.read_text(encoding="utf-8"), "old ipa")
            log = root / "build-out/logs/package-ipa-xcodebuild.log"
            self.assertEqual(
                log.read_text(encoding="utf-8"),
                "ld: Undefined symbols for architecture arm64\n"
                "  \"_missing_symbol\", referenced from:\n"
                "clang: error: linker command failed with exit code 1\n",
            )

    def test_success_signs_and_replaces_the_release_ipa(self) -> None:
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=os.name == "nt") as temporary:
            root, _ = self.make_fixture(pathlib.Path(temporary))
            output = root / "release" / "PSMobile.ipa"
            output.parent.mkdir()
            output.write_text("old ipa", encoding="utf-8")

            result = self.run_package(root, output, "success")

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(output.read_text(encoding="utf-8"), "fresh ipa\n")
            app = root / "build-out/ios-derived-device/Build/Products/Debug-iphoneos/PSMobile.app"
            self.assertTrue((app / ".signed").is_file())
            self.assertIn(self.bash_path(output), result.stdout)


if __name__ == "__main__":
    unittest.main()
