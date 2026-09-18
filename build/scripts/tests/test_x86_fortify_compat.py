from __future__ import annotations

import pathlib
import shutil
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
SHIM = ROOT / "core/src/psm_android_x86_fortify_compat.cpp"
CMAKE = ROOT / "CMakeLists.txt"
COMPILER = next(
    (shutil.which(candidate) for candidate in ("c++", "g++", "clang++") if shutil.which(candidate)),
    None,
)


class X86FortifyCompatTests(unittest.TestCase):
    """Android x86_64 receives only the missing GMP fortify bridge."""

    @unittest.skipUnless(COMPILER, "requires a C++ compiler")
    def test_fprintf_chk_forwards_variadic_arguments(self) -> None:
        self.assertTrue(SHIM.is_file(), "missing Android x86_64 fortify shim")
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            probe = root / "probe.cpp"
            executable = root / "probe"
            probe.write_text(
                "#include <cstdio>\n#include <cstring>\n"
                "extern \"C\" int __fprintf_chk(FILE*, int, const char*, ...);\n"
                "int main() { FILE* f = tmpfile(); if (!f) return 1; "
                "if (__fprintf_chk(f, 1, \"fortify-%d\", 9) != 9) return 2; "
                "rewind(f); char text[32] = {}; fread(text, 1, sizeof(text), f); "
                "fclose(f); return std::strcmp(text, \"fortify-9\"); }\n",
                encoding="utf-8",
            )
            built = subprocess.run(
                [COMPILER, "-std=c++17", str(SHIM), str(probe), "-o", str(executable)],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(built.returncode, 0, built.stdout + built.stderr)
            ran = subprocess.run([executable], text=True, capture_output=True, check=False)
            self.assertEqual(ran.returncode, 0, ran.stdout + ran.stderr)

    def test_cmake_compiles_shim_only_for_android_x86_64(self) -> None:
        cmake = CMAKE.read_text(encoding="utf-8")
        self.assertIn('ANDROID_ABI STREQUAL "x86_64"', cmake)
        self.assertIn("core/src/psm_android_x86_fortify_compat.cpp", cmake)


if __name__ == "__main__":
    unittest.main()
