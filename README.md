# Теремок

Учебный backend приложения для озвучивания текстов по ролям. Репозиторий является
одним Gradle multi-project на Java 21.

## Структура

```text
backend/
  auth-service/                    Spring Boot, регистрация, вход и JWT-аутентификация
  catalog-service/                 Spring Boot, каталог произведений
  text-work-content-service/       Spring Boot, структура и содержимое произведений
  draft-recordings-service/        черновые варианты записей и публикация роли
  recordings-service/              опубликованные роли и задания на рендер
  renderer-service/                Kafka worker с FFmpeg
  text-work-deployer/              консольная публикация JSON в Kafka
  database-exporter/               консольная выгрузка всех таблиц в XLSX
  backend-shared/
    kafka-events/                  общие контракты Kafka-событий
    jwt-auth/                      проверка JWT в защищённых сервисах
    object-storage/                общий клиент S3/MinIO
backend-client-shared/
  src/main/java/                    общие REST-контракты backend и клиентов
console-client/                    нативное консольное приложение
examples/
  text-work-added-*.json             примеры событий для трёх сказок
compose.yaml
docs/back_full.drawio              актуальная архитектурная диаграмма
```

Backend-сервисы не публикуют HTTP-порты на хост.
Единственная HTTP-точка входа — Traefik на `http://localhost:8080`. Kafka доступна
только внутри Docker-сети. PostgreSQL опубликован только на IPv4 loopback-интерфейсе
хоста как `127.0.0.1:15432`, чтобы к нему можно было подключиться через pgAdmin.

Один контейнер PostgreSQL содержит пять независимых баз:

- `auth_db` принадлежит `auth-service`;
- `catalog_db` принадлежит `catalog-service`;
- `text_work_content_db` принадлежит `text-work-content-service`;
- `draft_recordings_db` принадлежит `draft-recordings-service`;
- `recordings_db` принадлежит `recordings-service`.

Каждый сервис создаёт свои таблицы идемпотентным `schema.sql` при запуске. Отдельный
мигратор не используется.

### Подключение через pgAdmin

Сначала запустить или пересоздать контейнер PostgreSQL:

```shell
docker compose up -d --force-recreate postgres
```

В pgAdmin выбрать `Register Server` и указать параметры подключения:

| Параметр | Значение |
| --- | --- |
| Host name/address | `127.0.0.1` |
| Port | `15432` |
| Maintenance database | `postgres` |
| Username | `teremok_admin` |
| Password | `teremok_admin` |

После подключения в разделе `Databases` будут доступны `auth_db`, `catalog_db`,
`text_work_content_db`, `draft_recordings_db` и `recordings_db`. При необходимости можно подключаться сразу под сервисными
пользователями: `auth_service` / `auth_service` для `auth_db`,
`catalog_service` / `catalog_service` для `catalog_db` и
`text_work_content_service` / `text_work_content_service` для
`text_work_content_db`, `draft_recordings_service` / `draft_recordings_service` для
`draft_recordings_db` и `recordings_service` / `recordings_service` для `recordings_db`.

Хостовый порт можно заменить переменной `POSTGRES_HOST_PORT`, например:

```powershell
$env:POSTGRES_HOST_PORT = "25432"
docker compose up -d --force-recreate postgres
```

## Запуск backend

Требуется Docker Desktop. Команды одинаковы для Windows и macOS и выполняются из
корня репозитория.

Собрать и запустить Kafka, Traefik и все сервисы:

```shell
docker compose up --build -d
```

При старте одноразовые контейнеры `db-init` и `kafka-topics-init` создают недостающие
базы, таблицы и топики, в том числе при использовании существующих volumes.
Прикладные сервисы запускаются после их успешного завершения.

Создать отсутствующие сервисные БД, удалить существующие таблицы и сразу создать их
заново пустыми можно одной Docker-командой:

```shell
docker compose run --rm db-reset
```

`db-reset` отображается как отдельный одноразовый сервис Docker Compose и может
запускаться действием Run в Docker Desktop или из терминала. Он автоматически
поднимает контейнер PostgreSQL. Команда не удаляет Kafka-журнал и Docker volume
PostgreSQL, а очищает только таблицы приложений.

Отдельно создать все Kafka-топики:

```shell
docker compose run --rm kafka-topics-init
```

Команду создания топиков можно запускать повторно: она идемпотентна. Создаются
топики `text-work-added`, `user-created`, `role-recording-published`,
`render-requests` и `render-responses`.

Получить данные через Traefik:

```text
GET http://localhost:8080/api/v1/catalog/text-works
GET http://localhost:8080/api/v1/catalog/text-works/{id}
GET http://localhost:8080/api/v1/catalog/text-works/count
GET http://localhost:8080/api/v1/text-work-content/text-works/{id}
GET http://localhost:8080/api/v1/text-work-content/text-works/count
```

## Auth service

Регистрация создаёт пользователя, публикует `UserCreatedEvent` в отдельный Kafka-топик
`user-created` и сразу возвращает JWT. Пароль хранится только в виде BCrypt-хэша.
JWT содержит UUID пользователя в поле `sub` и действует семь дней.

```text
POST http://localhost:8080/api/v1/auth/register
POST http://localhost:8080/api/v1/auth/login
POST http://localhost:8080/api/v1/auth/logout
```

Тело запросов регистрации и входа:

```json
{
  "login": "reader",
  "password": "secret-password"
}
```

Регистрация отвечает статусом `201`, вход — `200`. Оба ответа содержат `userId`,
`accessToken`, `tokenType` и `expiresAt`. Для выхода нужно передать заголовок
`Authorization: Bearer <accessToken>`; сервис ответит `204`. Так как JWT не хранится
на сервере, logout означает удаление токена клиентом и не отзывает уже выданный токен.

Для локального стенда задан учебный JWT-секрет. Его можно заменить переменной
окружения `JWT_SECRET` длиной не менее 32 байт.

## Записи и рендер

`DraftRecordingsService` хранит неограниченное число пользовательских вариантов для
каждого фрагмента. Файл помещается в MinIO под префикс `draft/`, а метаданные — в
`draft_recordings_db`. Все методы требуют JWT в заголовке `Authorization`.

```text
POST   /api/v1/draft-recordings/fragments/{fragmentId}       multipart-поле file
GET    /api/v1/draft-recordings/roles/{roleId}
DELETE /api/v1/draft-recordings/{recordingId}
POST   /api/v1/draft-recordings/roles/{roleId}/publish
```

При публикации нужно передать ровно по одному выбранному черновику для каждого
фрагмента роли:

```json
{
  "selections": [
    { "fragmentId": "...", "draftRecordingId": "..." }
  ]
}
```

Выбранные исходники копируются в `published/`, их черновые записи удаляются, а
невыбранные варианты сохраняются. Сервис публикует `role-recording-published`, после
чего `RecordingsService` создаёт неизменяемую опубликованную озвучку роли.

```text
GET  /api/v1/recordings/text-works/{textWorkId}/roles
POST /api/v1/recordings/renders
GET  /api/v1/recordings/renders/{renderId}
```

Для рендера выбирается ровно одна опубликованная озвучка для каждой роли произведения.
Результат можно запросить одним MP3 или отдельным MP3 для каждого сегмента:

```json
{
  "textWorkId": "...",
  "outputMode": "SINGLE_FILE",
  "roles": [
    { "roleId": "...", "roleRecordingId": "..." }
  ]
}
```

Допустимые режимы: `SINGLE_FILE` и `BY_SEGMENTS`. `RecordingsService` создаёт задание
со статусом `PENDING` и отправляет `render-requests`. Stateless-воркер
`RendererService` скачивает опубликованные фрагменты из MinIO, нормализует их в WAV
PCM через FFmpeg, объединяет в порядке сегментов и фрагментов, сохраняет MP3 под
`renders/` и отвечает событием `render-responses`. Итоговый статус и object keys
доступны через `GET /api/v1/recordings/renders/{renderId}`.

MinIO доступен с хоста только на `http://127.0.0.1:19000`, его консоль — на
`http://127.0.0.1:19001`. Учебные учётные данные: `teremok` / `teremok-secret`.
Их можно заменить через `MINIO_ROOT_USER` и `MINIO_ROOT_PASSWORD`.

Образ MinIO собирается локально из официального исходника релиза
`RELEASE.2025-10-15T17-29-55Z` через `docker/minio/Dockerfile`. Первая сборка
скачивает Go-зависимости и занимает больше времени. Повторные сборки используют кэш.

Сквозная проверка уже запущенного стенда (Node.js 22+ и Docker CLI):

```shell
node scripts/verify-recordings-stack.mjs
```

Проверка создаёт тестовое произведение с двумя ролями и сегментами, двух пользователей,
40 вариантов одного фрагмента и два рендера. Тестовые записи остаются в БД для проверки
Excel-выгрузки. Итог записывается в `data/verification/recordings-smoke.json`.

Остановить стенд:

```shell
docker compose down
```

Kafka, PostgreSQL и MinIO хранят данные в Docker volumes. Чтобы удалить все три хранилища:

```shell
docker compose down --volumes
```

## TextWorkDeployer

Опубликовать готовые примеры:

```shell
docker compose run --build --rm text-work-deployer /examples/text-work-added-shapochka.json
docker compose run --build --rm text-work-deployer /examples/text-work-added-teremok.json
docker compose run --build --rm text-work-deployer /examples/text-work-added-kolobok.json
```

Для ручного ввода запустите deployer без пути, вставьте JSON и завершите ввод
отдельной строкой `END`:

```shell
docker compose run --build --rm text-work-deployer
```

Deployer сначала преобразует JSON в `TextWorkAddedEvent`, проверяет обязательные поля,
уникальность UUID и порядковых номеров, ссылки фрагментов на роли, счётчики фрагментов
и правила оригинала/перевода. Только после этого событие отправляется в Kafka с ключом
`textWork.id`. Оба сервиса получают каждое событие в собственных consumer groups и
сохраняют свои проекции в отдельных базах PostgreSQL. Повторная доставка события
безопасна: репозитории обновляют существующий агрегат в одной транзакции.

## Database exporter

Собрать данные всех сервисных БД в один Excel-файл:

```shell
docker compose run --build --rm database-exporter
```

Результат появится в `exports/teremok-database-export.xlsx`. Каждая таблица выгружается
на отдельный лист. В имени листа сервис и таблица разделены двойным подчёркиванием,
например `auth__user_account`, `catalog__catalog_text_work` и `content__voice_part`. Полное имя вида
`service-name__table-name`, фактическое имя листа, число строк и результат выгрузки
записываются в первый лист `_export_status`.

Если подключение к БД или чтение таблицы завершилось ошибкой, XLSX-файл всё равно
создаётся. На соответствующем листе записываются `DATA_NOT_RECEIVED` и текст ошибки,
а `_export_status` содержит тот же статус. Пустая доступная таблица считается успешно
полученной и имеет строку заголовков без строк данных.

## Console client

Console client запускается на хосте и обращается только к Traefik. По умолчанию адрес
backend — `http://localhost:8080`; его можно заменить переменной `BACKEND_URL`.

Windows:

```powershell
.\gradlew.bat :console-client:run
```

macOS/Linux:

```shell
./gradlew :console-client:run
```

При запуске клиент получает список произведений из `CatalogService`, для каждого из
них запрашивает текст и роли в `TextWorkContentService` и объединяет ответы по UUID.
Внутренняя модель клиента использует те же понятия: `TextWork`, `VoicePart` и
`TextWorkFragment`.
Локальные файлы произведений не используются обычным запуском. В главном меню также остаются
диагностические пункты с количеством записей в обоих сервисах.

## Сборка Java-проектов

```shell
./gradlew assemble
```

Spring-сервисы используют Spring Boot 4.1.1. Тесты сервисов запускаются командой:

```shell
./gradlew :backend:auth-service:test
./gradlew :backend:draft-recordings-service:test
./gradlew :backend:recordings-service:test
```
