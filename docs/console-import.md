# Консольный импорт произведений

Импорт выполняется через `TextWorkDeployer`. В веб-интерфейсе остаётся экспорт;
оба веб-пути к preview/apply возвращают 404. Backend сохраняет два отдельных
REST-запроса проверки и применения, как требует КР №3.

## CSV

Запустите Docker-стенд и соберите импортёр:

```powershell
.\mvnw.cmd -pl backend/text-work-deployer -am package -DskipTests
java -jar backend/text-work-deployer/target/text-work-deployer-jar-with-dependencies.jar --csv-template books.csv
```

Заполните шаблон. Затем проверьте файл без изменения данных:

```powershell
java -jar backend/text-work-deployer/target/text-work-deployer-jar-with-dependencies.jar --csv books.csv --check
```

Применение с подтверждением в терминале:

```powershell
java -jar backend/text-work-deployer/target/text-work-deployer-jar-with-dependencies.jar --csv books.csv --mode NEW
```

`NEW` добавляет только новые коды. `UPSERT` добавляет новые и обновляет существующие
по коду. Для явного применения без вопроса используйте `--yes`. `--check` и `--yes`
взаимоисключающие. Ошибки выводятся с номером строки; при любой ошибке файл не применяется.
После проверки импортёр обновляет access-токен перед применением и завершает сессию.

Нужна учётная запись редактора или администратора. В обычном терминале импортёр
спрашивает логин и пароль (пароль не отображается). Для запуска без интерактивного
ввода задайте `IMPORT_LOGIN`, `IMPORT_PASSWORD`; секрет не передаётся аргументом команды.
`BACKEND_URL` по умолчанию равен `http://localhost:8080`.

Для Docker укажите переменные в локальном `.env` и положите файл в `examples`:

```powershell
docker compose run --build --rm text-work-deployer --csv /examples/books.csv --check
docker compose run --rm text-work-deployer --csv /examples/books.csv --mode UPSERT --yes
```

Формат: UTF-8 (BOM допустим), запятая, стандартное CSV-экранирование кавычками;
до 1000 записей и 5 МБ. Заголовки:

```csv
code,title,author,description,language,collectionId,status,targetMinutes,featured,tags,script
```

`collectionId` — UUID действующей коллекции (у исходной художественной коллекции
`10000000-0000-0000-0000-000000000099`). Статус: `DRAFT`, `RECORDING`, `READY`.
Длительность: 1–1440 минут. `featured`: `true` или `false`.
Теги разделяются `;`, до 20 по 60 символов. Сценарий — строки `Роль: реплика`;
многострочный сценарий заключайте в кавычки. При обновлении сценарий с существующими
ролями должен оставаться прежним, чтобы записи не потеряли связи с репликами.

CSV из экспорта содержит дополнительные UUID, даты, версию, расчётные показатели
и исходный JSON. Импортёр принимает эти дополнительные колонки, но не переписывает
системные идентификаторы, даты и расчётные поля из файла.

## Прежний JSON

Публикация структурированного JSON в Kafka сохранена:

```powershell
docker compose run --build --rm text-work-deployer /examples/text-work-added-teremok.json
```

Без аргументов работает прежний ввод JSON до отдельной строки `END`.
