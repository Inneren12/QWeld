import json
import re

from qw_ru_lint import (
    GlossaryEntry,
    apply_glossary,
    lint_file,
    load_glossary,
    match_case,
)


def test_match_case_preserves_common_patterns():
    assert match_case("ladder", "лестница") == "лестница"
    assert match_case("Ladder", "лестница") == "Лестница"
    assert match_case("LADDER", "лестница") == "ЛЕСТНИЦА"


def test_apply_glossary_records_replacements():
    entry = GlossaryEntry(source="ladder", replacement="лестница", pattern=re.compile(r"ladder", re.IGNORECASE))
    updated, records = apply_glossary("Use a ladder", [entry], "stem")
    assert updated == "Use a лестница"
    assert records[0].path == "stem"


def test_lint_file_creates_diff_without_writing(tmp_path):
    glossary_path = tmp_path / "glossary.md"
    glossary_path.write_text(
        "| Англицизм | Предпочтительный термин |\n| --- | --- |\n| ladder | лестница |",
        encoding="utf-8",
    )
    entries = load_glossary(glossary_path)

    question_path = tmp_path / "question.json"
    question_path.write_text(
        json.dumps({"id": "Q-X_sample_1", "stem": "Inspect the ladder"}),
        encoding="utf-8",
    )

    report_dir = tmp_path / "reports"
    changed, records, diff_text = lint_file(question_path, entries, apply=False, report_dir=report_dir)

    assert changed is True
    assert "лестница" in diff_text
    assert "familyId" not in diff_text
    assert question_path.read_text(encoding="utf-8").count("лестница") == 0
    assert records
