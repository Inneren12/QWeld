#!/usr/bin/env bash
set -euo pipefail

THRESH=${THRESH:-0.60}
REPORT_PATH=${1:-"build/reports/kover/xml/report.xml"}

mapfile -t MODULE_REPORTS < <(
  find . -mindepth 2 -path "*/build/reports/kover/report.xml" \
    -not -path "./build/reports/kover/report.xml" | sort
)

if [[ ${#MODULE_REPORTS[@]} -eq 0 ]]; then
  echo "coverage-threshold: no module Kover reports were found" >&2
  exit 1
fi

mkdir -p "$(dirname "$REPORT_PATH")"

python3 - "$THRESH" "$REPORT_PATH" "${MODULE_REPORTS[@]}" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

if len(sys.argv) < 4:
    print("coverage-threshold: missing module reports", file=sys.stderr)
    sys.exit(1)

threshold = float(sys.argv[1])
report_path = Path(sys.argv[2])
module_reports = [Path(p) for p in sys.argv[3:]]

total_missed = 0
total_covered = 0

for module_report in module_reports:
    try:
        tree = ET.parse(module_report)
    except ET.ParseError as exc:
        print(f"coverage-threshold: failed to parse {module_report}: {exc}", file=sys.stderr)
        sys.exit(1)

    root = tree.getroot()
    module_name = root.get("name", module_report.parent.parent.name)
    for counter in root.iter("counter"):
        if counter.get("type") == "LINE":
            missed = int(counter.get("missed", "0"))
            covered = int(counter.get("covered", "0"))
            total_missed += missed
            total_covered += covered
            break
    else:
        print(f"coverage-threshold: no LINE counter found in {module_report}", file=sys.stderr)
        sys.exit(1)

total_lines = total_missed + total_covered
line_ratio = (total_covered / total_lines) if total_lines else 0.0

report_root = ET.Element("report", {"name": "Aggregated Kover report"})
ET.SubElement(
    report_root,
    "counter",
    {
        "type": "LINE",
        "missed": str(total_missed),
        "covered": str(total_covered),
    },
)
report_path.write_text(
    ET.tostring(report_root, encoding="utf-8", xml_declaration=True).decode("utf-8") + "\n"
)

print(f"coverage-threshold: line coverage={line_ratio:.2%} (threshold={threshold:.0%})")
if line_ratio + 1e-9 < threshold:
    print(
        f"coverage-threshold: coverage {line_ratio:.2%} is below the required {threshold:.0%}",
        file=sys.stderr,
    )
    sys.exit(1)
PY
