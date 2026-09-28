# ER-диаграмма базы данных «Теремка»

Схема — [`schema.sql`](schema.sql), начальные данные — [`seed.sql`](seed.sql).
Основная сущность — `voicings`: озвучка одной роли одним автором.
Картинка для отчёта — [`er-diagram.png`](er-diagram.png), та же схема на доске FigJam рядом с User Story Map.

```mermaid
erDiagram
    direction LR

    TEXT_WORKS ||--|{ VOICE_PARTS : "роли"
    TEXT_WORKS ||--|{ TEXT_WORK_FRAGMENTS : "фрагменты"
    VOICE_PARTS ||--|{ TEXT_WORK_FRAGMENTS : "произносит"
    VOICE_PARTS ||--o{ VOICINGS : "озвучки роли"
    PROFILES ||--o{ VOICINGS : "автор"
    VOICINGS ||--o{ VOICING_RECORDINGS : "записи"
    TEXT_WORK_FRAGMENTS ||--o{ VOICING_RECORDINGS : "запись фрагмента"
    VOICINGS ||--o{ VOTES : "оценки"
    PROFILES ||--o{ VOTES : "ставит"

    PROFILES {
        varchar id PK "имя маленькими буквами"
        varchar name UK
        timestamptz created_at
    }
    TEXT_WORKS {
        uuid id PK
        varchar title
        varchar authors
        varchar language
    }
    VOICE_PARTS {
        uuid id PK
        uuid text_work_id FK
        varchar name "уникально в произведении"
    }
    TEXT_WORK_FRAGMENTS {
        uuid id PK
        uuid text_work_id FK
        int number "сквозной номер, уникален в произведении"
        uuid voice_part_id FK
        varchar content
    }
    VOICINGS {
        bigint id PK
        uuid voice_part_id FK
        varchar author_id FK
        varchar status "DRAFT, PUBLISHED, ARCHIVED"
        timestamptz created_at
        timestamptz published_at "обязательно, если не DRAFT"
    }
    VOICING_RECORDINGS {
        bigint voicing_id PK, FK
        uuid fragment_id PK, FK
        varchar audio_path UK "путь к WAV в папке записей"
        int duration_ms "от 0 до 120000"
        timestamptz recorded_at
    }
    VOTES {
        bigint voicing_id PK, FK
        varchar profile_id PK, FK
        varchar kind "LIKE, DISLIKE"
        timestamptz voted_at
    }
```

## Связи и ограничения

| Связь | Тип | Что гарантирует база |
| --- | --- | --- |
| `text_works` → `voice_parts` | один ко многим | имя роли уникально внутри произведения |
| `text_works` → `text_work_fragments` | один ко многим | номер фрагмента уникален внутри произведения и больше нуля |
| `voice_parts` → `text_work_fragments` | один ко многим | фрагмент принадлежит существующей роли |
| `voice_parts` → `voicings`, `profiles` → `voicings` | один ко многим | один автор озвучивает роль один раз: `UNIQUE (voice_part_id, author_id)` |
| `voicings` → `voicing_recordings` | один ко многим | фрагмент записан в озвучке один раз (составной первичный ключ); удаление озвучки удаляет записи |
| `voicings` → `votes`, `profiles` → `votes` | многие ко многим через `votes` | один голос от человека за озвучку (составной первичный ключ); удаление озвучки удаляет голоса |

Статус озвучки ограничен `CHECK`: только `DRAFT`, `PUBLISHED`, `ARCHIVED`, а у опубликованной
или архивной обязательно заполнено `published_at`. Голос — только `LIKE` или `DISLIKE`.
Длительность записи — от 0 до 2 минут, как и лимит записи в приложении.

Сами записи — WAV-файлы на диске (16 кГц, 16 бит, моно). В базе хранится путь к файлу
относительно папки записей (`console-client/data/audio` по умолчанию) и длительность.
