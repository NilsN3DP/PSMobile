from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
PATCH_SCRIPT = ROOT / "build/scripts/_patch_vgcode.py"
COMPILER = next(
    (shutil.which(candidate) for candidate in ("c++", "g++", "clang++") if shutil.which(candidate)),
    None,
)


class LibVGCodePatchTests(unittest.TestCase):
    """The mobile wrapper must stay compilable without wxWidgets headers."""

    def write_fixture(self, root: pathlib.Path) -> pathlib.Path:
        script_dir = root / "build/scripts"
        script_dir.mkdir(parents=True)
        shutil.copy2(PATCH_SCRIPT, script_dir / "_patch_vgcode.py")

        prusa = root / "external/PrusaSlicer"
        viewer_hpp = prusa / "src/libvgcode/src/ViewerImpl.hpp"
        viewer_hpp.parent.mkdir(parents=True)
        viewer_hpp.write_text(
            "void set_positions(const std::vector<Vec3>& positions);\n"
            "void set_heights_widths_angles(const std::vector<Vec3>& heights_widths_angles);\n",
            encoding="utf-8",
        )
        (viewer_hpp.with_name("ViewerImpl.cpp")).write_text(
            "void ViewerImpl::TextureData::set_positions(const std::vector<Vec3>& positions) {}\n"
            "void ViewerImpl::TextureData::set_heights_widths_angles(const std::vector<Vec3>& heights_widths_angles) {}\n"
            "GL_RGB32F 0, GL_RGB, GL_FLOAT GL_RGB, GL_FLOAT, &positions\n"
            "GL_RGB, GL_FLOAT, &heights_widths_angles\n"
            "positions.second * sizeof(Vec3)\n"
            "heights_widths_angles.second * sizeof(Vec3)\n",
            encoding="utf-8",
        )

        wrapper = prusa / "src/slic3r/GUI/LibVGCode/LibVGCodeWrapper.hpp"
        wrapper.parent.mkdir(parents=True)
        wrapper.write_text(
            "#pragma once\n"
            "#include \"slic3r/GUI/GUI_Preview.hpp\"\n"
            "namespace libvgcode {\n"
            "struct EOptionType {};\n"
            "extern EOptionType convert(const Slic3r::GUI::Preview::OptionType& type);\n"
            "}\n",
            encoding="utf-8",
        )
        (wrapper.with_suffix(".cpp")).write_text(
            "#include \"../GUI_Preview.hpp\"\n"
            "namespace libvgcode { struct EOptionType {}; }\n"
            "EOptionType convert(const Slic3r::GUI::Preview::OptionType& type) { return {}; }\n",
            encoding="utf-8",
        )
        preview = prusa / "src/slic3r/GUI/GUI_Preview.hpp"
        preview.parent.mkdir(parents=True, exist_ok=True)
        preview.write_text("#include <wx/panel.h>\n", encoding="utf-8")
        return prusa

    def compile_wrapper(self, root: pathlib.Path, prusa: pathlib.Path) -> subprocess.CompletedProcess[str]:
        source = root / "probe.cpp"
        source.write_text(
            "#include \"slic3r/GUI/LibVGCode/LibVGCodeWrapper.hpp\"\nint main() {}\n",
            encoding="utf-8",
        )
        return subprocess.run(
            [COMPILER, "-std=c++17", "-DPSM_NO_GUI_TYPES", "-fsyntax-only", "-I", str(prusa / "src"), str(source)],
            text=True,
            capture_output=True,
            check=False,
        )

    @unittest.skipUnless(COMPILER, "requires a C++ compiler for the wx guard proof")
    def test_psm_no_gui_types_removes_wx_panel_header(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            prusa = self.write_fixture(root)

            before = self.compile_wrapper(root, prusa)
            self.assertNotEqual(before.returncode, 0)
            self.assertIn("wx/panel.h", before.stderr)

            patched = subprocess.run(
                [sys.executable, str(root / "build/scripts/_patch_vgcode.py")],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(patched.returncode, 0, patched.stdout + patched.stderr)

            after = self.compile_wrapper(root, prusa)
            self.assertEqual(after.returncode, 0, after.stdout + after.stderr)

            rerun = subprocess.run(
                [sys.executable, str(root / "build/scripts/_patch_vgcode.py")],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(rerun.returncode, 0, rerun.stdout + rerun.stderr)
            self.assertEqual(self.compile_wrapper(root, prusa).returncode, 0)


if __name__ == "__main__":
    unittest.main()
