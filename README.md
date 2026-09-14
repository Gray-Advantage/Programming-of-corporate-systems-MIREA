# Programming of corporate systems — МИРЭА

Проект по дисциплине «Программирование корпоративных систем», РТУ МИРЭА.

Проект называется «Теремок» — это приложение для озвучивания книг по ролям.
Пользователь записывает голосом реплики персонажа и публикует роль, а другие
собирают книгу из ролей разных авторов, слушают её и голосуют за лучшие озвучки.

## Структура

Под каждую платформу пишется своё приложение в своей папке. Папки независимы:
у каждой своя сборка, свои зависимости и свой README с инструкцией по запуску.

| Папка | Платформа | Стек |
| --- | --- | --- |
| [`console/`](console/) | Консольное приложение | Java 21, Gradle, без зависимостей в рантайме |

Новая платформа добавляется отдельной папкой рядом и строкой в эту таблицу.

## Быстрый старт

Консольная версия, нужен JDK 21.

macOS (`brew install openjdk@21`):

    cd console
    export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
    ./gradlew -q --console=plain run

Windows, PowerShell (`winget install EclipseAdoptium.Temurin.21.JDK`):

    cd console
    .\gradlew.bat installDist
    .\build\install\teremok\bin\teremok.bat

Подробная инструкция для обеих систем, настройка микрофона и описание команд — в
[console/README.md](console/README.md).
