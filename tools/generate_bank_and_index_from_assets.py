#!/usr/bin/env python3
from __future__ import annotations

import json
import hashlib
from pathlib import Path
from typing import Dict, List, Any

# Конфиг — подправь, если нужно
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
    """Читает questions/<locale>/tasks/<taskId>.json и проверяет базовые инварианты."""
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

        # Обязательные поля на уровне вопроса
        missing = [k for k in ("id", "taskId", "stem", "choices", "correctId") if k not in q]
        if missing:
            raise ValueError(f"{task_path}: question #{idx} is missing required fields: {missing}")

        if q.get("taskId") != task_id:
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
        app-android/src/main/assets/questions/index.json (root агрегатор)
    """
    # Для root-индекса: locale -> map path->sha
    root_locale_files: Dict[str, Dict[str, str]] = {}

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
        # task meta нужно только для статистики и root-индекса, но sha мы всё равно считаем
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

        # Сортируем плоский банк по id / taskId
        all_questions.sort(
            key=lambda q: (str(q.get("id", "")), str(q.get("taskId", "")))
        )

        # Пишем банк
        bank_path.parent.mkdir(parents=True, exist_ok=True)
        with bank_path.open("w", encoding="utf-8") as f:
            json.dump(all_questions, f, ensure_ascii=False, indent=2)

        bank_sha = sha256_of_file(bank_path)

        # files: map path -> sha (именно это ждёт IndexParser.collectFiles)
        files_map: Dict[str, str] = {}

        # meta/task_labels.json (если есть)
        labels_path = meta_dir / "task_labels.json"
        if labels_path.is_file():
            labels_sha = sha256_of_file(labels_path)
            files_map[f"questions/{locale}/meta/task_labels.json"] = labels_sha
        else:
            print(
                f"[WARN] Locale {locale!r}: no meta/task_labels.json at {labels_path}"
            )

        # банк
        files_map[f"questions/{locale}/bank.v1.json"] = bank_sha

        # все task-бандлы
        for t in tasks_meta:
            files_map[t["path"]] = t["sha256"]

        # per-locale index.json в формате, который понимает IndexParser
        locale_index = {
            "schema": "questions-locale-index-v1",
            "locale": locale,
            "blueprintId": BLUEPRINT_ID,
            "bankVersion": BANK_VERSION,
            "files": files_map,  # ВАЖНО: именно объект, а не массив
        }

        locale_index_path.parent.mkdir(parents=True, exist_ok=True)
        with locale_index_path.open("w", encoding="utf-8") as f:
            json.dump(locale_index, f, ensure_ascii=False, indent=2)

        # копим для root-индекса
        root_locale_files[locale] = files_map

    # root questions/index.json (агрегатор по локалям)
    if not root_locale_files:
        print("[WARN] No locales processed, root index will not be written")
        return

    locales_obj: Dict[str, Any] = {}
    for locale, files_map in sorted(root_locale_files.items()):
        locales_obj[locale] = {
            "files": files_map
        }

    root_index = {
        "schema": "questions-index-v1",
        "blueprintId": BLUEPRINT_ID,
        "bankVersion": BANK_VERSION,
        "locales": locales_obj,
    }

    root_index_path = questions_root / "index.json"
    with root_index_path.open("w", encoding="utf-8") as f:
        json.dump(root_index, f, ensure_ascii=False, indent=2)

    print(f"[INFO] Wrote root index to {root_index_path}")


def main() -> None:
    # Скрипт предполагает, что лежит в <repo>/tools/
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
