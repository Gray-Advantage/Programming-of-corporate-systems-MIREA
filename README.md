# Теремок

Учебный backend приложения для озвучивания текстов по ролям. Репозиторий является
одним Gradle multi-project на Java 21.

## Структура

```text
backend/
  catalog-service/                 Spring Boot, каталог произведений
  text-work-content-service/       Spring Boot, структура и содержимое произведений
  text-work-deployer/              консольная публикация JSON в Kafka
  backend-shared/
    kafka-events/                  общий контракт события text-work-added
backend-client-shared/
  placeholder.txt
console-client/                    нативное консольное приложение
examples/
  text-work-added.json
compose.yaml
```

`CatalogService` и `TextWorkContentService` не публикуют порты на хост. Единственная
HTTP-точка входа — Traefik на `http://localhost:8080`. Kafka также доступна только
внутри Docker-сети.

## Запуск backend

Требуется Docker Desktop. Команды одинаковы для Windows и macOS и выполняются из
корня репозитория.

Собрать и запустить Kafka, Traefik и оба сервиса:

```shell
docker compose up --build -d
```

Отдельно создать все Kafka-топики:

```shell
docker compose run --rm kafka-topics-init
```

Команду создания топиков можно запускать повторно: она идемпотентна. Сейчас создаётся
один топик `text-work-added`.

Проверить количество записей через Traefik:

```text
GET http://localhost:8080/api/v1/catalog/text-works/count
GET http://localhost:8080/api/v1/text-work-content/text-works/count
```

Остановить стенд:

```shell
docker compose down
```

Kafka хранит журнал в Docker volume. Чтобы удалить и его:

```shell
docker compose down --volumes
```

## TextWorkDeployer

Опубликовать готовый пример:

```shell
docker compose run --build --rm text-work-deployer /examples/text-work-added.json
```

Для ручного ввода запустите deployer без пути, вставьте JSON и завершите ввод
отдельной строкой `END`:

```shell
docker compose run --build --rm text-work-deployer
```

Deployer сначала преобразует JSON в `TextWorkAddedEvent`, проверяет обязательные поля,
а затем отправляет событие в Kafka с ключом `textWork.id`. Оба сервиса получают каждое
событие в собственных consumer groups и сохраняют свои проекции в in-memory
репозиториях.

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

В главном меню есть отдельные пункты для количества записей в `CatalogService` и
`TextWorkContentService`.

## Сборка Java-проектов

```shell
./gradlew assemble
```

Spring-сервисы используют Spring Boot 4.1.1. Тестовая инфраструктура для новых
компонентов намеренно не добавлена.
