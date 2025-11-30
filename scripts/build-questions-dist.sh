#!/usr/bin/env bash
set -euo pipefail

# Источник (можно передать аргументом, напр. "app-android/src/main/assets/questions")
SRC_ROOT="${1:-dist/questions}"

# Мета
blueprintId="welder_ip_sk_202404"
bankVersion="v1"

# --- хэширование с нормализацией EOL для *.json ---
hash_json() {          # CRLF/CR -> LF, стабильные SHA на Win/Linux
  tr -d '\r' < "$1" | sha256sum | awk '{print $1}'
}
hash_bin() { sha256sum "$1" | awk '{print $1}'; }

# --- детализированный индекс (старый формат) для локали ---
build_index_for_locale () {
  local loc="$1"
  local out="$SRC_ROOT/$loc/index.json"
  local base="$SRC_ROOT/$loc"

  test -d "$base" || { echo "skip $loc (no $base)"; return 0; }

  mapfile -t files < <(find "$base" -type f \( -name "*.json" -o -name "*.gz" \) | LC_ALL=C sort)

  {
    echo '{'
    echo "  \"blueprintId\": \"$blueprintId\","
    echo "  \"bankVersion\": \"$bankVersion\","
    echo '  "files": {'
    local first=1
    for f in "${files[@]}"; do
      local rel="questions/${loc}/${f#"$base/"}"
      local ext="${f##*.}"
      local sha
      if [[ "$ext" == "json" ]]; then sha="$(hash_json "$f")"; else sha="$(hash_bin "$f")"; fi
      if [[ $first -eq 0 ]]; then echo ','; fi
      printf '    "%s": "%s"' "$rel" "$sha"
      first=0
    done
    echo
    echo '  }'
    echo '}'
  } > "$out"

  echo "Wrote $out"
}

# --- выбор SHA для таска (предпочитаем .gz, потом .json) ---
pick_task_sha() {
  local dir="$1" id="$2"
  local gz="$dir/$id.gz" js="$dir/$id.json"
  if [[ -f "$gz" ]]; then hash_bin "$gz"
  elif [[ -f "$js" ]]; then hash_json "$js"
  else echo ""; fi
}

# --- summary индекс (questions-index-v1) на корне SRC_ROOT ---
build_summary_index () {
  local out="$SRC_ROOT/index.json"
  local locales=()
  for loc in en ru; do
    if [[ -d "$SRC_ROOT/$loc" ]]; then locales+=("$loc"); fi
  done
  if [[ ${#locales[@]} -eq 0 ]]; then
    echo "No locales under $SRC_ROOT"; return 0
  fi

  {
    echo '{'
    echo '  "schema": "questions-index-v1",'
    echo "  \"blueprintId\": \"$blueprintId\","
    echo "  \"bankVersion\": \"$bankVersion\","
    echo '  "locales": {'
    local lfirst=1
    for loc in "${locales[@]}"; do
      [[ $lfirst -eq 1 ]] || echo ','
      lfirst=0
      local base="$SRC_ROOT/$loc"
      local bank="$base/bank.v1.json"
      local bankSha=""; [[ -f "$bank" ]] && bankSha="$(hash_json "$bank")"

      # соберём SHA для всех tasks/*.{json|gz} (уникально по taskId)
      declare -A taskSha=()
      if [[ -d "$base/tasks" ]]; then
        while IFS= read -r f; do
          fname="$(basename "$f")"
          id="${fname%.*}"            # A-1.json -> A-1
          # предпочтём .gz, если есть обе версии
          if [[ -z "${taskSha[$id]:-}" ]] || [[ "$fname" == "$id.gz" ]]; then
            if [[ "$fname" == "$id.json" ]]; then taskSha["$id"]="$(hash_json "$f")"
            else taskSha["$id"]="$(hash_bin "$f")"
            fi
          fi
        done < <(find "$base/tasks" -type f \( -name "*.json" -o -name "*.gz" \) | LC_ALL=C sort)
      fi

      echo "    \"$loc\": {"
      # total/tasks (размеры) можно опустить; парсер у нас их делает опциональными
      echo '      "sha256": {'
      echo "        \"bank\": \"${bankSha}\","
      echo '        "tasks": {'
      local tfirst=1
      for id in $(printf "%s\n" "${!taskSha[@]}" | LC_ALL=C sort); do
        [[ $tfirst -eq 1 ]] || echo ','
        tfirst=0
        printf '          "%s": "%s"' "$id" "${taskSha[$id]}"
      done
      echo
      echo '        }'
      echo '      }'
      echo '    }'
      unset taskSha
    done
    echo '  }'
    echo '}'
  } > "$out"

  echo "Wrote $out"
}

# --- run ---
build_index_for_locale en
build_index_for_locale ru
build_summary_index

echo "Copy dist → assets (choose one):"
echo "  rsync -a --delete dist/questions/ app-android/src/main/assets/questions/"
echo "  # Windows:"
echo "  robocopy dist\\questions app-android\\src\\main\\assets\\questions /MIR"
