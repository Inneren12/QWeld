from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List


@dataclass
class FamilyUpdate:
    path: Path
    previous: str | None
    new: str


def compute_family_id(question_id: str) -> str:
    parts = question_id.split("_")
    if len(parts) <= 1:
        return question_id
    return "_".join(parts[:-1])


def ensure_family_id(data: dict) -> tuple[bool, str | None, str | None]:
    question_id = data.get("id")
    if not question_id:
        return False, None, None

    desired_family_id = compute_family_id(question_id)
    current_family_id = data.get("familyId")

    if current_family_id:
        return False, current_family_id, desired_family_id

    data["familyId"] = desired_family_id
    return True, current_family_id, desired_family_id


def iter_question_files(content_root: Path) -> Iterable[Path]:
    for path in sorted(content_root.rglob("*.json")):
        if path.is_file():
            yield path


def process_file(path: Path, apply: bool) -> FamilyUpdate | None:
    with path.open("r", encoding="utf-8") as fh:
        original_text = fh.read()
    data = json.loads(original_text)

    changed, previous, new_value = ensure_family_id(data)
    if not changed:
        return None

    if apply:
        with path.open("w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write("\n")

    return FamilyUpdate(path=path, previous=previous, new=new_value or "")


def process(content_root: Path, apply: bool) -> List[FamilyUpdate]:
    updates: List[FamilyUpdate] = []
    for path in iter_question_files(content_root):
        result = process_file(path, apply)
        if result:
            updates.append(result)
    return updates


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Populate missing familyId fields in localized question JSON files.",
    )
    parser.add_argument(
        "--content-root",
        type=Path,
        default=Path(__file__).resolve().parents[2] / "content" / "questions",
        help="Root directory containing localized question JSON files (default: %(default)s)",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply the changes to disk. Without this flag the script runs in dry-run mode.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    updates = process(args.content_root, apply=args.apply)

    if updates:
        print(f"Updated familyId in {len(updates)} file(s).")
        for update in updates:
            previous_display = update.previous or "<empty>"
            print(f" - {update.path}: {previous_display} -> {update.new}")
        if not args.apply:
            print("Dry-run mode enabled; rerun with --apply to write changes.")
    else:
        print("No missing familyId fields detected.")
    return 0


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    raise SystemExit(main())
