#!/usr/bin/env python3
"""Validiert und rendert den belegbaren PSMobile-Funktionsstand."""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
from collections import Counter, defaultdict


ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_MATRIX = ROOT / "docs" / "feature-matrix.json"
VALID_STATUS = {
    "coded",
    "built",
    "emulator_tested",
    "device_tested",
    "hardware_tested",
    "blocked",
    "not_started",
}
TESTED_STATUS = {"emulator_tested", "device_tested", "hardware_tested"}


def load_matrix(path: pathlib.Path) -> dict:
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def evidence_path(value: str) -> pathlib.Path:
    return ROOT / value.split("#", 1)[0]


def validate(matrix: dict) -> list[str]:
    errors: list[str] = []
    if matrix.get("schema") != 1:
        errors.append("schema muss 1 sein")

    features = matrix.get("features")
    if not isinstance(features, list):
        return errors + ["features muss eine Liste sein"]

    ids: set[str] = set()
    for index, feature in enumerate(features):
        prefix = f"features[{index}]"
        feature_id = feature.get("id")
        if not isinstance(feature_id, str) or not feature_id:
            errors.append(f"{prefix}: id fehlt")
            continue
        if feature_id in ids:
            errors.append(f"{feature_id}: id ist doppelt")
        ids.add(feature_id)

        status = feature.get("status")
        if status not in VALID_STATUS:
            errors.append(f"{feature_id}: ungueltiger Status {status!r}")

        evidence = feature.get("evidence")
        if not isinstance(evidence, list) or not evidence:
            errors.append(f"{feature_id}: mindestens ein Evidenzpfad fehlt")
        else:
            for value in evidence:
                if not isinstance(value, str) or not value:
                    errors.append(f"{feature_id}: ungueltiger Evidenzpfad")
                elif not evidence_path(value).exists():
                    errors.append(f"{feature_id}: Evidenz fehlt: {value}")

        if status in TESTED_STATUS and not feature.get("verification"):
            errors.append(f"{feature_id}: getesteter Status braucht verification")
        if status == "hardware_tested" and not any(
            "hardware" in value.lower() or "device" in value.lower()
            for value in feature.get("verification", [])
        ):
            errors.append(
                f"{feature_id}: hardware_tested braucht einen Hardware-/Geraetebeleg"
            )

        blocked_by = feature.get("blocked_by", [])
        if not isinstance(blocked_by, list):
            errors.append(f"{feature_id}: blocked_by muss eine Liste sein")

    return errors


def markdown(matrix: dict) -> str:
    grouped: dict[str, list[dict]] = defaultdict(list)
    for feature in matrix["features"]:
        grouped[feature["platform"]].append(feature)

    lines = [
        "# PSMobile – belegbarer Funktionsstand",
        "",
        f"Stand: {matrix.get('updated', 'unbekannt')}. "
        "Die Statuswerte stammen aus `docs/feature-matrix.json`.",
        "",
        "Status bedeutet: `coded` = Quellcode vorhanden, `built` = in einem "
        "Build geprüft, `*_tested` = in der genannten Umgebung ausgeführt. "
        "Ein vorhandener Codepfad ist damit ausdrücklich noch kein Gerätetest.",
        "",
    ]
    for platform in sorted(grouped):
        features = sorted(grouped[platform], key=lambda item: item["id"])
        counts = Counter(feature["status"] for feature in features)
        lines.extend(
            [
                f"## {platform}",
                "",
                " | ".join(
                    f"{status}: {counts[status]}"
                    for status in sorted(counts)
                ),
                "",
                "| Funktion | Status | Blockiert durch |",
                "| --- | --- | --- |",
            ]
        )
        for feature in features:
            blockers = ", ".join(feature.get("blocked_by", [])) or "–"
            lines.append(
                f"| {feature['title']} | `{feature['status']}` | {blockers} |"
            )
        lines.append("")

    blocked = [
        feature for feature in matrix["features"]
        if feature["status"] in {"blocked", "not_started"}
        or feature.get("blocked_by")
    ]
    lines.extend(["## Offene Blocker", ""])
    for feature in blocked:
        blockers = ", ".join(feature.get("blocked_by", [])) or "noch nicht begonnen"
        lines.append(f"- `{feature['id']}`: {blockers}")
    lines.append("")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix", type=pathlib.Path, default=DEFAULT_MATRIX)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--markdown", type=pathlib.Path)
    args = parser.parse_args(argv)

    try:
        matrix = load_matrix(args.matrix)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"Matrix nicht lesbar: {exc}", file=sys.stderr)
        return 1

    errors = validate(matrix)
    if errors:
        for error in errors:
            print(f"FEHLER: {error}", file=sys.stderr)
        return 1

    rendered = markdown(matrix)
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(rendered, encoding="utf-8")
    elif not args.check:
        print(rendered, end="")

    if args.check:
        print(f"OK: {len(matrix['features'])} Feature-Eintraege")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
