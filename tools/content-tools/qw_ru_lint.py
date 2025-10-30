from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from difflib import unified_diff
from pathlib import Path
from typing import Iterable, List, Sequence


@dataclass
class GlossaryEntry:
    source: str
    replacement: str
    pattern: re.Pattern[str]


@dataclass
class ReplacementRecord:
    path: str
    source_text: str
    replacement_text: str
    glossary_source: str


def load_glossary(glossary_path: Path) -> List[GlossaryEntry]:
    if not glossary_path.exists():
        raise FileNotFoundError(f"Glossary file not found: {glossary_path}")

    entries: List[GlossaryEntry] = []
    for line in glossary_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or not stripped.startswith("|"):
            continue
        cells = [cell.strip() for cell in stripped.strip("|").split("|")]
        if len(cells) < 2:
            continue
        if set(cells[0]) <= {"-", ":", " "}:
            # separator row
            continue
        if cells[0].lower() == "англицизм":
            continue
        source, replacement = cells[0], cells[1]
        if not source or not replacement:
            continue
        pattern = re.compile(rf"\b{re.escape(source)}\b", re.IGNORECASE)
        entries.append(GlossaryEntry(source=source, replacement=replacement, pattern=pattern))
    return entries


def match_case(original: str, replacement: str) -> str:
    if original.isupper():
        return replacement.upper()
    if original.istitle():
        return replacement.title()
    if original and original[0].isupper():
        return replacement[0].upper() + replacement[1:]
    return replacement


def apply_glossary(text: str, entries: Sequence[GlossaryEntry], path: str) -> tuple[str, List[ReplacementRecord]]:
    records: List[ReplacementRecord] = []
    updated = text
    for entry in entries:
        def _replacer(match: re.Match[str]) -> str:
            replaced = match_case(match.group(0), entry.replacement)
            records.append(
                ReplacementRecord(
                    path=path,
                    source_text=match.group(0),
                    replacement_text=replaced,
                    glossary_source=entry.source,
                )
            )
            return replaced

        updated = entry.pattern.sub(_replacer, updated)
    return updated, records


def iter_question_files(content_root: Path) -> Iterable[Path]:
    for path in sorted(content_root.rglob("*.json")):
        if path.is_file():
            yield path


def lint_node(node, entries: Sequence[GlossaryEntry], path: str = "") -> tuple[object, List[ReplacementRecord], bool]:
    records: List[ReplacementRecord] = []
    changed = False

    if isinstance(node, dict):
        for key, value in list(node.items()):
            new_value, sub_records, sub_changed = lint_node(value, entries, f"{path}.{key}" if path else key)
            if sub_changed:
                node[key] = new_value
            records.extend(sub_records)
            changed = changed or sub_changed
        return node, records, changed

    if isinstance(node, list):
        for index, value in enumerate(node):
            new_value, sub_records, sub_changed = lint_node(value, entries, f"{path}[{index}]")
            if sub_changed:
                node[index] = new_value
            records.extend(sub_records)
            changed = changed or sub_changed
        return node, records, changed

    if isinstance(node, str):
        updated, replacements = apply_glossary(node, entries, path)
        if replacements:
            changed = True
        return updated, replacements, changed

    return node, records, changed


def lint_file(path: Path, entries: Sequence[GlossaryEntry], apply: bool, report_dir: Path | None) -> tuple[bool, List[ReplacementRecord], str | None]:
    original_text = path.read_text(encoding="utf-8")
    data = json.loads(original_text)

    _, records, changed = lint_node(data, entries)

    if not changed:
        return False, [], None

    new_text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"

    diff_text = "".join(
        unified_diff(
            original_text.splitlines(keepends=True),
            new_text.splitlines(keepends=True),
            fromfile=str(path),
            tofile=str(path),
        )
    )

    if apply:
        path.write_text(new_text, encoding="utf-8")

    if report_dir:
        report_dir.mkdir(parents=True, exist_ok=True)
        report_path = report_dir / f"{path.name}.diff"
        report_path.write_text(diff_text, encoding="utf-8")

    return True, records, diff_text


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Lint Russian localized content for anglicisms using the shared glossary.",
    )
    parser.add_argument(
        "--content-root",
        type=Path,
        default=Path(__file__).resolve().parents[2] / "content" / "questions",
        help="Root directory containing localized question JSON files (default: %(default)s)",
    )
    parser.add_argument(
        "--glossary",
        type=Path,
        default=Path(__file__).resolve().parents[2] / "docs" / "glossary_ru.md",
        help="Path to the Russian terminology glossary (default: %(default)s)",
    )
    parser.add_argument(
        "--report-dir",
        type=Path,
        default=Path(__file__).resolve().parents[2] / "logs" / "diffs" / "ru_lint",
        help="Where to store per-file diff reports (default: %(default)s)",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply the replacements to disk. Without this flag the script runs in dry-run mode.",
    )
    return parser


def summarize(records: Sequence[ReplacementRecord]) -> str:
    lines = []
    for record in records:
        lines.append(
            f" * {record.path}: '{record.source_text}' -> '{record.replacement_text}' (glossary: {record.glossary_source})"
        )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    entries = load_glossary(args.glossary)

    all_records: List[ReplacementRecord] = []
    changed_files = 0
    report_dir = args.report_dir
    if not args.apply and report_dir:
        # ensure reports reflect dry-run results but do not persist writes when directory is missing
        report_dir.mkdir(parents=True, exist_ok=True)

    for path in iter_question_files(args.content_root):
        changed, records, diff_text = lint_file(path, entries, apply=args.apply, report_dir=report_dir)
        if changed:
            changed_files += 1
            all_records.extend(records)
            if diff_text:
                print(diff_text)

    print(f"Processed {changed_files} file(s) with replacements.")
    if all_records:
        print("Replacements:")
        print(summarize(all_records))
    else:
        print("No glossary replacements were necessary.")

    if not args.apply:
        print("Dry-run mode enabled; rerun with --apply to write changes.")

    return 0


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    raise SystemExit(main())
