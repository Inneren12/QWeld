# QWeld Schema Validation - Installation Guide

## Что нужно сделать

### 1. Скопировать файлы в репозиторий

Скопируйте всю директорию `tools/` из этого архива в корень вашего QWeld репозитория:

```
QWeld/
├── tools/
│   └── schema-validation/
│       ├── schemas/
│       │   └── welder_blueprint.schema.json
│       ├── .gitignore
│       ├── package.json
│       └── README.md
├── app-android/
├── .github/
└── ...
```

### 2. Установить зависимости

```bash
cd tools/schema-validation
npm install
```

### 3. Обновить ваш root index.json

Ваш текущий `app-android/src/main/assets/questions/index.json` должен выглядеть так:

```json
{
  "schema": "questions-index-v1",
  "generatedAt": "2025-12-08T20:00:00Z",
  "locales": {
    "en": {
      "total": 606,
      "tasks": {
        "A-1": {
          "sha256": "2e77a653f5c0e62a970d308e4aaf5ec26d00cb332323186e6021afaa6b754fdc"
        },
        "A-2": {
          "sha256": "5e459b301fb6a7c68e96bfbdc3ca72e87b0e51b81b41e6f8b8baeb653a670b80"
        }
        // ... остальные tasks
      },
      "sha256": {
        "sha256": "bef88497c2a1ee538b579702b06da67bf47de029cbfbcb83a4979a8aa599a033"
      }
    },
    "ru": {
      "total": 636,
      "tasks": {
        // ... аналогично
      },
      "sha256": {
        "sha256": "364e6d7a4dd40320a842957867692f8292c7b1fdff041d29719744c74ed75f14"
      }
    }
  }
}
```

**Важно:** 
- `tasks` - это объект где каждый task ID мапится на `{sha256: "..."}`
- `sha256.sha256` - это хеш файла `{locale}/index.json`

### 4. Проверить валидацию

```bash
cd tools/schema-validation

# Проверить root index
npm run validate:root

# Проверить locale manifests
npm run validate:locales

# Проверить всё
npm run validate:all
```

### 5. Закоммитить изменения

```bash
git add tools/schema-validation/
git add app-android/src/main/assets/questions/index.json
git commit -m "Add JSON Schema validation for question indexes"
git push
```

## Структура файлов

### Root Index (questions/index.json)

```json
{
  "schema": "questions-index-v1",           // Версия схемы
  "generatedAt": "2025-12-08T20:00:00Z",   // ISO timestamp
  "locales": {
    "{locale}": {
      "total": 606,                         // Общее кол-во вопросов
      "tasks": {
        "{taskId}": {
          "sha256": "..."                   // SHA256 хеш task файла
        }
      },
      "sha256": {
        "sha256": "..."                     // SHA256 хеш {locale}/index.json
      }
    }
  }
}
```

### Locale Manifest (questions/{locale}/index.json)

```json
{
  "blueprintId": "welder_ip_sk_202404",    // ID blueprint
  "bankVersion": "v1",                      // Версия банка
  "files": {
    "questions/{locale}/...": {
      "sha256": "..."                       // SHA256 хеш файла
    }
  }
}
```

## CI/CD

После установки, ваш GitHub Actions workflow автоматически будет валидировать файлы при каждом PR.

Если возникнут ошибки валидации, проверьте:

1. **Root index** имеет правильную структуру (см. выше)
2. **Locale manifests** имеют `blueprintId`, `bankVersion`, `files`
3. Все SHA256 хеши lowercase и 64 символа
4. Task IDs соответствуют паттерну `{LETTER}-{NUMBER}` (например, `A-1`)

## Дополнительная информация

Полная документация по схеме находится в:
- `tools/schema-validation/README.md`
- `tools/schema-validation/schemas/welder_blueprint.schema.json` (комментарии внутри схемы)

## Генерация индексов

Используйте `tools/content/gen_questions_indexes.js` для автоматической генерации правильных индексных файлов.
