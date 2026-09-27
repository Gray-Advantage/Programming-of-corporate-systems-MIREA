# Console client

Нативное консольное приложение на Java 21. Оно не входит в Docker Compose, чтобы
микрофон и звук одинаково работали на Windows и macOS.

Запуск из корня репозитория:

```powershell
.\gradlew.bat :console-client:run
```

```shell
./gradlew :console-client:run
```

Для обращения к backend должен быть запущен Docker-стенд. Клиент ходит к Traefik по
адресу `http://localhost:8080`. Другой адрес задаётся переменной окружения
`BACKEND_URL`.
