import json
from pathlib import Path

from qw_fix_familyid import compute_family_id, process_file


def test_compute_family_id_strips_trailing_chunk():
    assert compute_family_id("Q-A-2_example_20000014") == "Q-A-2_example"
    assert compute_family_id("SINGLE") == "SINGLE"


def test_process_file_adds_family_id(tmp_path):
    file_path = tmp_path / "question.json"
    payload = {"id": "Q-A-3_sample_10000001", "stem": "text"}
    file_path.write_text(json.dumps(payload), encoding="utf-8")

    update = process_file(file_path, apply=False)
    assert update is not None
    assert json.loads(file_path.read_text(encoding="utf-8")).get("familyId") is None

    update = process_file(file_path, apply=True)
    assert update is not None
    written = json.loads(file_path.read_text(encoding="utf-8"))
    assert written["familyId"] == "Q-A-3_sample"
