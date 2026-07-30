#!/usr/bin/env python3
"""Fingerprint aller Quellen, die in libpsmobile_core.so eingehen."""

from __future__ import annotations

import argparse
import hashlib
import pathlib
import subprocess


ROOT = pathlib.Path(__file__).resolve().parents[2]
INPUTS = (
    ROOT / "CMakeLists.txt",
    ROOT / "cmake",
    ROOT / "core" / "include",
    ROOT / "core" / "src",
    ROOT / "viewport" / "include",
    ROOT / "viewport" / "src",
    ROOT / "android" / "jni",
)


def files() -> list[pathlib.Path]:
    result: list[pathlib.Path] = []
    for source in INPUTS:
        if source.is_file():
            result.append(source)
        elif source.is_dir():
            result.extend(
                path
                for path in source.rglob("*")
                if path.is_file()
                and "core/src/generated" not in path.relative_to(ROOT).as_posix()
            )
    return sorted(result, key=lambda path: path.relative_to(ROOT).as_posix())


def prusaslicer_revision() -> str:
    checkout = ROOT / "external" / "PrusaSlicer"
    try:
        return subprocess.check_output(
            ["git", "-C", str(checkout), "rev-parse", "HEAD"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        # Auf Windows-SMB kann Git den Checkout wegen "dubious
        # ownership" ablehnen, obwohl die Dateien lesbar sind. HEAD
        # direkt aufzulösen ist für den Fingerprint ausreichend und
        # verhindert, dass dieselben Quellen je Host einmal mit
        # "missing" und einmal mit dem echten Commit gehasht werden.
        marker = checkout / ".git"
        git_dir = marker
        try:
            if marker.is_file():
                value = marker.read_text(encoding="utf-8").strip()
                if not value.startswith("gitdir:"):
                    return "missing"
                git_dir = (marker.parent / value.split(":", 1)[1].strip()).resolve()

            head = (git_dir / "HEAD").read_text(encoding="ascii").strip()
            if not head.startswith("ref:"):
                return head
            ref = head.split(":", 1)[1].strip()
            loose = git_dir / ref
            if loose.is_file():
                return loose.read_text(encoding="ascii").strip()
            packed = git_dir / "packed-refs"
            if packed.is_file():
                for line in packed.read_text(encoding="ascii").splitlines():
                    if line and not line.startswith(("#", "^")):
                        sha, name = line.split(" ", 1)
                        if name == ref:
                            return sha
        except (OSError, ValueError):
            pass
        return "missing"


def calculate(abi: str, api: str, build_type: str) -> str:
    digest = hashlib.sha256()
    digest.update(f"abi={abi}\napi={api}\ntype={build_type}\n".encode())
    digest.update(f"prusaslicer={prusaslicer_revision()}\n".encode())
    for path in files():
        relative = path.relative_to(ROOT).as_posix()
        digest.update(relative.encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--abi", required=True)
    parser.add_argument("--api", default="26")
    parser.add_argument("--build-type", default="Release")
    parser.add_argument("--write", type=pathlib.Path)
    args = parser.parse_args()

    value = calculate(args.abi, args.api, args.build_type)
    if args.write:
        args.write.parent.mkdir(parents=True, exist_ok=True)
        args.write.write_text(value + "\n", encoding="utf-8")
    else:
        print(value)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
