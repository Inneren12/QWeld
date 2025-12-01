#!/usr/bin/env python3
from __future__ import annotations

import json
import hashlib
from pathlib import Path
from typing import Dict, List, Any


# Конфиг — при необходимости подправь под себя
BLUEPRINT_ID = "welder_ip_sk_202404"
BANK_VERSION = "v1"
LOCALES = ["en", "ru"]


def sha256_of_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def load_task_questions(task_path: Path, locale: str, task_id: str) -> List[Dict[str, Any]]:
    with task_path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    if not isinstance(data, list):
        raise ValueError(
            f"{task_path}: expected JSON array of questions, got {type(data).__name__}"
        )

    questions: List[Dict[str, Any]] = []
    for idx, q in enumerate(data):
        if not isinstance(q, dict):
            raise ValueError(f"{task_path}: question #{idx} is not an object")

        # Обязательные поля
        missing = [k for k in ("id", "taskId", "stem", "choices", "correctId") if k not in q]
        if missing:
            raise ValueError(f"{task_path}: question #{idx} is missing required fields: {missing}")

        if q.get("taskId") != task_id:
            # taskId в payload должен совпадать с именем файла
            raise ValueError(
                f"{task_path}: question #{idx} has taskId={q.get('taskId')!r}, "
                f"expected {task_id!r}"
            )

        choices = q.get("choices")
        if not isinstance(choices, list) or not choices:
            raise ValueError(f"{task_path}: question #{idx} has invalid 'choices'")

        choice_ids = set()
        for c_idx, c in enumerate(choices):
            if not isinstance(c, dict):
                raise ValueError(
                    f"{task_path}: question #{idx} choice #{c_idx} is not an object"
                )
            if "id" not in c or "text" not in c:
                raise ValueError(
                    f"{task_path}: question #{idx} choice #{c_idx} "
                    f"is missing 'id' or 'text'"
                )
            cid = str(c["id"])
            if cid in choice_ids:
                raise ValueError(
                    f"{task_path}: question #{idx} has duplicate choice id {cid!r}"
                )
            choice_ids.add(cid)

        correct_id = str(q["correctId"])
        if correct_id not in choice_ids:
            raise ValueError(
                f"{task_path}: question #{idx} has correctId={correct_id!r} "
                f"which is not present in choices {sorted(choice_ids)}"
            )

        questions.append(q)

    return questions


def build_banks_and_indexes(questions_root: Path) -> None:
    """
    Собирает bank.v1.json и per-locale index.json для LOCALES,
    используя агрегированные таски из:

        app-android/src/main/assets/questions/<locale>/tasks/<taskId>.json

    Пишет:

        app-android/src/main/assets/questions/<locale>/bank.v1.json
        app-android/src/main/assets/questions/<locale>/index.json
        app-android/src/main/assets/questions/index.json
    """
    locale_summaries = {}

    for locale in LOCALES:
        tasks_dir = questions_root / locale / "tasks"
        meta_dir = questions_root / locale / "meta"
        bank_path = questions_root / locale / "bank.v1.json"
        locale_index_path = questions_root / locale / "index.json"

        if not tasks_dir.is_dir():
            print(f"[WARN] Locale {locale!r}: tasks dir not found at {tasks_dir}, skipping")
            continue

        print(f"[INFO] Building bank and index for locale {locale!r}")

        task_files = sorted(tasks_dir.glob("*.json"))
        if not task_files:
            print(f"[WARN] Locale {locale!r}: no task JSON files found in {tasks_dir}, skipping")
            continue

        all_questions: List[Dict[str, Any]] = []
        tasks_meta: List[Dict[str, Any]] = []

        for task_file in task_files:
            task_id = task_file.stem
            qs = load_task_questions(task_file, locale, task_id)
            all_questions.extend(qs)

            tasks_meta.append(
                {
                    "taskId": task_id,
                    "path": f"questions/{locale}/tasks/{task_file.name}",
                    "sha256": sha256_of_file(task_file),
                    "questionCount": len(qs),
                }
            )

        # Сортировка: по id, затем по taskId
        all_questions.sort(
            key=lambda q: (str(q.get("id", "")), str(q.get("taskId", "")))
        )

        # Пишем банк как плоский массив вопросов
        bank_path.parent.mkdir(parents=True, exist_ok=True)
        with bank_path.open("w", encoding="utf-8") as f:
            json.dump(all_questions, f, ensure_ascii=False, indent=2)

        bank_sha = sha256_of_file(bank_path)

        # Meta labels (если есть)
        labels_path = meta_dir / "task_labels.json"
        labels_entry = None
        labels_sha = None
        if labels_path.is_file():
            labels_sha = sha256_of_file(labels_path)
            labels_entry = {
                "kind": "meta",
                "path": f"questions/{locale}/meta/task_labels.json",
                "sha256": labels_sha,
            }
        else:
            print(
                f"[WARN] Locale {locale!r}: no meta/task_labels.json at {labels_path}"
            )

        # Локальный index.json для локали
        files_entries: List[Dict[str, Any]] = []

        if labels_entry is not None:
            files_entries.append(labels_entry)

        files_entries.append(
            {
                "kind": "bank",
                "path": f"questions/{locale}/bank.v1.json",
                "sha256": bank_sha,
            }
        )

        for t in tasks_meta:
            files_entries.append(
                {
                    "kind": "task",
                    "taskId": t["taskId"],
                    "path": t["path"],
                    "sha256": t["sha256"],
                    "questionCount": t["questionCount"],
                }
            )

        locale_index = {
            "schema": "questions-locale-index-v1",
            "locale": locale,
            "blueprintId": BLUEPRINT_ID,
            "bankVersion": BANK_VERSION,
            "files": files_entries,
        }

        locale_index_path.parent.mkdir(parents=True, exist_ok=True)
        with locale_index_path.open("w", encoding="utf-8") as f:
            json.dump(locale_index, f, ensure_ascii=False, indent=2)

        # Сводка для корневого индекса
        locale_summaries[locale] = {
            "tasks": tasks_meta,
            "bankPath": f"questions/{locale}/bank.v1.json",
            "bankSha256": bank_sha,
            "labelsPath": labels_entry["path"] if labels_entry else None,
            "labelsSha256": labels_sha,
            "questionCount": len(all_questions),
        }

    # Корневой questions/index.json
    if not locale_summaries:
        print("[WARN] No locales processed, root index will not be written")
        return

    locales_entries: List[Dict[str, Any]] = []
    for locale, summary in sorted(locale_summaries.items()):
        entry: Dict[str, Any] = {
            "locale": locale,
            "tasks": len(summary["tasks"]),
            "questions": summary["questionCount"],
            "bank": {
                "path": summary["bankPath"],
                "sha256": summary["bankSha256"],
            },
        }
        if summary["labelsPath"] and summary["labelsSha256"]:
            entry["meta"] = {
                "path": summary["labelsPath"],
                "sha256": summary["labelsSha256"],
            }
        entry["taskBundles"] = [
            {
                "taskId": t["taskId"],
                "path": t["path"],
                "sha256": t["sha256"],
                "questionCount": t["questionCount"],
            }
            for t in summary["tasks"]
        ]
        locales_entries.append(entry)

    root_index = {
        "schema": "questions-index-v1",
        "blueprintId": BLUEPRINT_ID,
        "bankVersion": BANK_VERSION,
        "locales": locales_entries,
    }

    root_index_path = questions_root / "index.json"
    with root_index_path.open("w", encoding="utf-8") as f:
        json.dump(root_index, f, ensure_ascii=False, indent=2)

    print(f"[INFO] Wrote root index to {root_index_path}")


def main() -> None:
    # Предполагаем, что скрипт лежит в <repo>/tools/
    # и запускается из корня репо.
    repo_root = Path(__file__).resolve().parents[1]
    questions_root = repo_root / "app-android" / "src" / "main" / "assets" / "questions"

    if not questions_root.is_dir():
        raise SystemExit(
            f"questions assets dir not found at {questions_root}. "
            "Adjust the path in this script to match your project layout."
        )

    print(f"[INFO] Using questions root: {questions_root}")
    build_banks_and_indexes(questions_root)


if __name__ == "__main__":
    main()
