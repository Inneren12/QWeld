# Release Gate — R0 Baseline (“Freeze”)

> **Purpose:** зафиксировать один канонический набор проверок, которые **обязаны быть зелёными** перед релизом, и исключить “плавающие” регрессии.

---

## 0) Metadata

- **Target version:** `<e.g. 1.0.0>`
- **Track:** `<internal | closed testing | open testing | production>`
- **Branch / Tag:** `<release/r0 | tag>`
- **Baseline commit (SHA):** `<SHA>`
- **Freeze date (local):** `<YYYY-MM-DD>`
- **Owner:** `<name>`
- **Timezone:** `America/Edmonton`

### Freeze rules (R0)
- [ ] В ветку релиза попадают **только bugfix / стабилизация** (без новых фич).
- [ ] Любой PR в release baseline = **только при зелёных required checks** + прохождение локальных gates (см. ниже).
- [ ] Любые “обходы” (skip/disable) → только через запись в **Known limitations** + явное согласование в PR.

---

## 1) References (source of truth)

**Project docs**
- `README.md`
- `stage.md`
- `PROJECT_OVERVIEW.md`
- `MODULES.md`

**Content / QA**
- `CONTENT_GUIDE.md`
- `release_checks.md` (если существует)
- `required-checks.md`
- `ci-content.md`
- `blueprint-rules.md`
- `questions-index-findings.md` (если существует)

**Policy / Compliance**
- `PRIVACY.md`
- `content-policy.md` / `CONTENT_POLICY.md`
- `COMPLIANCE.md`
- `firestore_security_notes.md` (если существует)
- `error_handling_manual_checklist.md` + `manual_error_network_tests.md` (если существует)
- `glossary_ru.json` / `glossary_ru.md` (если существует)

---

## 2) Preconditions (environment)

### Tooling
- **JDK:** `<version>`
- **Gradle:** `<wrapper version>` (use `./gradlew --version`)
- **Android SDK / build-tools:** `<installed>`
- **Node.js:** `<version>` (recommend: 20 LTS) — required for content scripts/validators
- **ADB:** `<version>` (use `adb version`)

### Clean-environment protocol (must for baseline)
- [ ] `git status` чистый
- [ ] `git clean -xfd` выполнен (⚠️ удалит неотслеживаемые файлы)
- [ ] Gradle запускается с `--no-daemon`
- [ ] Запуск команд **в указанном порядке** (см. Gates)

> **Note:** если есть “dist → assets” шаги для вопросов, сначала делаем rebuild dist, затем синхронизацию assets, затем `:app-android:verifyAssets`.

---

## 3) Release Gates (MUST PASS)

### 3.1 Local Gates (run on a workstation)

#### Gate A — Android Unit + Assets (MUST)
- [ ] PASS

```bash
./gradlew --no-daemon --stacktrace clean test :app-android:verifyAssets
PASS criteria

Exit code 0

Unit tests: 0 failed

:app-android:verifyAssets: PASS (все required assets/indexes/sha согласованы)

Gate B — Device / Instrumentation (MUST)
 PASS

bash
Copy code
./gradlew --no-daemon --stacktrace connectedAndroidTest
PASS criteria

Exit code 0

Instrumentation tests: 0 failed

Нет флаков “из-за окружения” (если флак обнаружен → фикс/стабилизация теста в R0)

Gate C — Release artifacts (SHOULD for baseline; MUST если релиз собираем сейчас)
 PASS (если MUST/включено)

bash
Copy code
./gradlew --no-daemon --stacktrace :app-android:lintRelease :app-android:bundleRelease
PASS criteria

Exit code 0

bundleRelease produced successfully (AAB)

Release lint без блокирующих ошибок

3.2 Content Gates (MUST PASS for baseline)
Для R0 baseline делаем FULL RUN, даже если в CI есть режим --changed-only.

Gate D — Blueprint validator (MUST)
 PASS

bash
Copy code
bash scripts/validate-blueprint.sh
PASS criteria

Exit code 0

Нет schema/structure ошибок

Gate E — Questions validator (MUST)
 PASS

bash
Copy code
bash scripts/validate-questions.sh
PASS criteria

Exit code 0

Нет ошибок структуры/формата вопросов

Gate F — Quotas / totals check (MUST)
 PASS

bash
Copy code
bash scripts/check-quotas.sh
PASS criteria

Exit code 0

Totals/quotas соответствуют профилю/ожиданиям

Gate G — Snapshot verification (MUST, если используется)
 PASS

bash
Copy code
bash scripts/generate-blueprint-snapshots.sh verify
PASS criteria

Exit code 0

Snapshots не изменились (или изменения сознательные и отражены в PR)

Gate H — Explanation coverage (RUN + record; WARN допустим)
 RUN

 Coverage recorded below

bash
Copy code
bash scripts/check-explanation-coverage.sh
Record results

EN coverage: <%>

RU coverage: <%>

Notes: <what is missing / thresholds / warnings>

3.3 Asset manifest consistency (MUST)
Чтобы не ловить расхождения между root/per-locale индексами и checksum-ами.

 PASS

 Подтверждено: изменения sha256 в questions/<locale>/index.json отражены там, где это требуется (root summary/manifest/dist — согласно проектным правилам).

 :app-android:verifyAssets подтверждает целостность.

4) CI Gates (Required checks must be GREEN)
Список должен совпадать с Branch protection / required checks.

 Android CI / build — GREEN

 Content Validators (quick) / content — GREEN

 Coverage gate (min line coverage) — GREEN (threshold: <e.g. 60%>)

 (optional/non-blocking) Android UI smoke / ui-smoke — GREEN или documented FAIL (см. Known limitations)

Evidence

CI run link: <url or GH actions run id>

Commit SHA: <SHA>

5) Device Matrix (for Gate B)
Type	Device	Android/API	ABI	Result	Notes
Emulator	<name>	<API>	<x86_64/arm64>	<PASS/FAIL>	<…>
Physical (optional)	<model>	<API>	<arm64>	<PASS/FAIL>	<…>

6) Gate Run Log (evidence)
Date	Commit	Env (OS/JDK/Gradle)	Device	Gates	Result	Log link / Notes
<YYYY-MM-DD>	<SHA>	<…>	<emulator/physical>	A+B+D+E+F+G(+C)	<PASS/FAIL>	<…>
<YYYY-MM-DD>	<SHA>	<…>	<…>	<…>	<PASS/FAIL>	<…>

7) Known limitations (must be honest for release notes)
Заполняем по факту, и сверяем со stage.md. Всё, что “не закрыли” — фиксируем здесь.

7.1 Localization (RU)
 RU локаль включена

 Fallback на EN возможен в частях контента/объяснений (expected)

What exactly falls back: <list>

User impact: <low/medium/high>

7.2 Explanations coverage
EN coverage: <%>

RU coverage: <%>

Impact: <e.g., explanations missing for some questions; rationales still present>

Follow-up: <issue/task id>

7.3 Practice / presets / UX
Limitations: <describe>

Follow-up: <issue/task id>

7.4 Resume / process death robustness
Limitations: <describe>

Follow-up: <issue/task id>

7.5 Analytics / Crash / reporting
Limitations: <describe>

Follow-up: <issue/task id>

7.6 Admin / debug-only tooling
Limitations: <describe>

Follow-up: <issue/task id>

8) Manual sanity (short, but required for baseline)
Не заменяет тесты. Цель — поймать “очевидные” runtime регрессии.

 Exam flow: start → answer 3–5 questions → rotate/background → continue

 Timer/resume: kill app process → reopen → resume expected state

 Practice flow: open → answer 5 questions → review screen (если есть)

 Locale switch: EN ↔ RU smoke (main labels, settings, key screens)

 Offline reporting (если включено): отправка репорта в offline → очередь → retry online

Notes / evidence: <short notes>

9) Policy & Compliance (release hygiene)
Privacy & Content
 PRIVACY.md актуален (Last updated: <YYYY-MM-DD>)

 Content policy доступна и соответствует текущему поведению приложения

 Links (policy/standards) проверены: <links.md or equivalent>

Firebase / Firestore (если используется)
 Security rules reviewed (write-only where needed, no public reads for sensitive collections)

 No PII stored in reports beyond allowed fields

Compliance (recommended for baseline)
 “Code Compliance (minimal)” workflow run on <SHA>

Artifact link: <…>

Notes: <reuse-lint, SBOM, licenses, jscpd results>

10) Sign-off (DoD)
 docs/release_gate.md заполнен и лежит в репо

 All MUST gates PASS (A, B, D, E, F, G if used; H recorded; manifest consistency OK)

 CI required checks GREEN

 Gate runs recorded (≥1 emulator, желательно +1 физ. девайс)

 Known limitations заполнены и соответствуют stage.md

 Manual sanity выполнен

Approved by: <name>
Date: <YYYY-MM-DD>

markdown
Copy code

Если хочешь — могу сразу адаптировать этот шаблон под твою фактическую структуру команд (как именно у вас называются скрипты/таски) *без “TBD”*, но для этого нужно либо прислать 1–2 реальных примера команд из README/CI (или просто кусок `./gradlew tasks` + `scripts/` список).





