# Теремок

Учебный backend приложения для озвучивания текстов по ролям. Репозиторий является
одним Gradle multi-project на Java 21.

## Структура

```text
backend/
  catalog-service/                 Spring Boot, каталог произведений
  text-work-content-service/       Spring Boot, структура и содержимое произведений
  text-work-deployer/              консольная публикация JSON в Kafka
  database-exporter/               консольная выгрузка всех таблиц в XLSX
  backend-shared/
    kafka-events/                  общий контракт события text-work-added
backend-client-shared/
  src/main/java/                    общие REST-контракты backend и клиентов
console-client/                    нативное консольное приложение
examples/
  text-work-added-*.json             примеры событий для трёх сказок
compose.yaml
```

`CatalogService` и `TextWorkContentService` не публикуют порты на хост. Единственная
HTTP-точка входа — Traefik на `http://localhost:8080`. Kafka и PostgreSQL также
доступны только внутри Docker-сети.

Один контейнер PostgreSQL содержит две независимые базы:

- `catalog_db` принадлежит `catalog-service`;
- `text_work_content_db` принадлежит `text-work-content-service`.

Каждый сервис создаёт свои таблицы идемпотентным `schema.sql` при запуске. Отдельный
мигратор не используется.

## Запуск backend

Требуется Docker Desktop. Команды одинаковы для Windows и macOS и выполняются из
корня репозитория.

Собрать и запустить Kafka, Traefik и оба сервиса:

```shell
docker compose up --build -d
```

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

Команду создания топиков можно запускать повторно: она идемпотентна. Сейчас создаётся
один топик `text-work-added`.

Получить данные через Traefik:

```text
GET http://localhost:8080/api/v1/catalog/text-works
GET http://localhost:8080/api/v1/catalog/text-works/{id}
GET http://localhost:8080/api/v1/catalog/text-works/count
GET http://localhost:8080/api/v1/text-work-content/text-works/{id}
GET http://localhost:8080/api/v1/text-work-content/text-works/count
```

Остановить стенд:

```shell
docker compose down
```

Kafka и PostgreSQL хранят данные в Docker volumes. Чтобы удалить оба хранилища:

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

Собрать данные обеих сервисных БД в один Excel-файл:

```shell
docker compose run --build --rm database-exporter
```

Результат появится в `exports/teremok-database-export.xlsx`. Каждая таблица выгружается
на отдельный лист. В имени листа сервис и таблица разделены двойным подчёркиванием,
например `catalog__catalog_text_work` и `content__voice_part`. Полное имя вида
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

Spring-сервисы используют Spring Boot 4.1.1. Тестовая инфраструктура для новых
компонентов намеренно не добавлена.
