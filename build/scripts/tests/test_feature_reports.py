from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[3]
PYTHON = sys.executable


class FeatureReportTests(unittest.TestCase):
    def run_script(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [PYTHON, *args],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_feature_matrix_is_valid(self) -> None:
        result = self.run_script("build/scripts/feature-report.py", "--check")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_invalid_status_and_missing_evidence_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            matrix = pathlib.Path(directory) / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "features": [
                            {
                                "id": "broken",
                                "title": "Broken",
                                "platform": "android",
                                "status": "finished",
                                "evidence": ["does/not/exist"],
                                "blocked_by": [],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            result = self.run_script(
                "build/scripts/feature-report.py",
                "--matrix",
                str(matrix),
                "--check",
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ungueltiger Status", result.stderr)
        self.assertIn("Evidenz fehlt", result.stderr)

    def test_hardware_status_needs_hardware_verification(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            matrix = pathlib.Path(directory) / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "features": [
                            {
                                "id": "too-optimistic",
                                "title": "Too optimistic",
                                "platform": "android",
                                "status": "hardware_tested",
                                "evidence": ["README.md"],
                                "verification": ["README.md"],
                                "blocked_by": [],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            result = self.run_script(
                "build/scripts/feature-report.py",
                "--matrix",
                str(matrix),
                "--check",
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Hardware-/Geraetebeleg", result.stderr)

    def test_gap_report_supports_explicit_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory) / "gaps.txt"
            result = self.run_script(
                "build/scripts/gap-report.py",
                "external/PrusaSlicer",
                "android/app/src/main/assets/psui",
                "--output",
                str(output),
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(output.is_file())
            self.assertIn("bed_shape", output.read_text(encoding="utf-8"))

    def test_gap_report_stdout_does_not_write_default_file(self) -> None:
        default_output = ROOT / "build-out" / "gap-report.txt"
        before = default_output.stat().st_mtime_ns if default_output.exists() else None
        result = self.run_script(
            "build/scripts/gap-report.py",
            "external/PrusaSlicer",
            "android/app/src/main/assets/psui",
            "--output",
            "-",
        )
        after = default_output.stat().st_mtime_ns if default_output.exists() else None
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("bed_shape", result.stdout)
        self.assertEqual(before, after)

    def test_gap_report_keeps_first_gizmo_after_a_leading_comment(self) -> None:
        """A comment before Move must not hide the first enum member."""
        with tempfile.TemporaryDirectory() as directory:
            prusaslicer = pathlib.Path(directory) / "PrusaSlicer"
            hpp = (
                prusaslicer
                / "src/slic3r/GUI/Gizmos/GLGizmosManager.hpp"
            )
            hpp.parent.mkdir(parents=True)
            hpp.write_text(
                """enum EType {
    // Order must match index in m_gizmos!
    Move,
    Scale,
    Undefined
};
""",
                encoding="utf-8",
            )
            config = prusaslicer / "src/libslic3r/PrintConfig.cpp"
            config.parent.mkdir(parents=True)
            config.write_text("", encoding="utf-8")
            matrix = pathlib.Path(directory) / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "features": [
                            {
                                "id": "mobile-move-and-scale",
                                "title": "Mobile move and scale",
                                "platform": "android",
                                "status": "built",
                                "evidence": ["README.md"],
                                "blocked_by": [],
                                "desktop_gizmos": ["Move", "Scale"],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            result = self.run_script(
                "build/scripts/gap-report.py",
                str(prusaslicer),
                "android/app/src/main/assets/psui",
                "--matrix",
                str(matrix),
                "--output",
                "-",
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PrusaSlicer (ohne SLA)  2", result.stdout)
        self.assertIn("bei uns im Code         2", result.stdout)

    def test_gap_report_labels_unimplemented_and_excluded_desktop_commands(self) -> None:
        """A stale matrix mapping and an explicit desktop exclusion stay distinguishable."""
        with tempfile.TemporaryDirectory() as directory:
            matrix = pathlib.Path(directory) / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "features": [
                            {
                                "id": "unimplemented-open",
                                "title": "Unimplemented open command",
                                "platform": "android",
                                "status": "not_started",
                                "evidence": ["README.md"],
                                "blocked_by": [],
                                "desktop_menu": ["&Open Project"],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            result = self.run_script(
                "build/scripts/gap-report.py",
                "external/PrusaSlicer",
                "android/app/src/main/assets/psui",
                "--matrix",
                str(matrix),
                "--output",
                "-",
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("MATRIX NICHT UMGESETZT  &Open Project", result.stdout)
        self.assertIn("AUSGENOMMEN: ZIP       Import ZIP Archive", result.stdout)

    def test_gap_report_separates_available_special_dialogs_from_open_ones(self) -> None:
        """Implemented mobile dialogs must not inflate the remaining parameter gap."""
        result = self.run_script(
            "build/scripts/gap-report.py",
            "external/PrusaSlicer",
            "android/app/src/main/assets/psui",
            "--output",
            "-",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("in PSMobile erreichbar           330", result.stdout)
        self.assertIn("ueber Spezialdialog erreichbar  9", result.stdout)
        self.assertIn("echte Dialog-Luecken             9", result.stdout)

    def test_generated_project_covers_profile_script_and_two_beds(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory) / "project.3mf"
            result = self.run_script(
                "build/scripts/make-test-project-3mf.py",
                str(output),
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            with zipfile.ZipFile(output) as archive:
                config = archive.read("Metadata/Slic3r_PE.config").decode()
                model = archive.read("3D/3dmodel.model").decode()
            self.assertIn("printer_settings_id = PSMobile Test Printer", config)
            self.assertIn("post_process = must-not-run", config)
            self.assertEqual(model.count("<item objectid="), 2)

    def test_toggle_source_is_reproducible_and_has_one_definition(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory) / "toggles.cpp"
            result = self.run_script(
                "build/scripts/extract-toggles.py",
                "external/PrusaSlicer",
                str(output),
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            expected = (
                ROOT / "core/src/psmobile_toggles_generated.cpp"
            ).read_text(encoding="utf-8")
            self.assertEqual(output.read_text(encoding="utf-8"), expected)

        sources = [
            ROOT / "core/src/psmobile_toggles.hpp",
            ROOT / "core/src/psmobile_toggles.cpp",
            ROOT / "core/src/psmobile_toggles_generated.cpp",
        ]
        definitions = sum(
            source.read_text(encoding="utf-8").count(
                "struct ToggleCollector"
            )
            for source in sources
        )
        self.assertEqual(definitions, 1)


if __name__ == "__main__":
    unittest.main()
