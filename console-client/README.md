# Console client

Нативное консольное приложение на Java 21. Оно не входит в Docker Compose, чтобы
микрофон и звук одинаково работали на Windows и macOS.

Запуск из корня репозитория:

```powershell
.\mvnw.cmd -pl console-client -am package -DskipTests
java -jar console-client/target/console-client-jar-with-dependencies.jar
```

```shell
./mvnw -pl console-client -am package -DskipTests
java -jar console-client/target/console-client-jar-with-dependencies.jar
```

Для обращения к backend должен быть запущен Docker-стенд. Клиент ходит к Traefik по
адресу `http://localhost:8080`. Другой адрес задаётся переменной окружения
`BACKEND_URL`.

Список произведений загружается из `CatalogService`, а сегменты, фрагменты и роли —
из `TextWorkContentService`. Перед запуском клиента опубликуйте примеры через
`TextWorkDeployer`, как описано в корневом README.

В коде клиента произведение представлено как `TextWork`, роль — как `VoicePart`, а
минимальная часть текста для записи — как `TextWorkFragment`.

Локальные метаданные озвучки используют поля `textWork` и `voicePart`, а аудиофайлы
называются `fragment-NNNN.wav`. Старый локальный формат `book/speaker/line-NNNN.wav`
после перехода на UUID backend не поддерживается.
