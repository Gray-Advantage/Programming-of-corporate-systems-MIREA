package space.grayt.teremok.deployer;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Console-only CSV workflow using the assignment's preview/apply REST endpoints. */
final class CsvImporter {
    static final String HEADERS = "code,title,author,description,language,collectionId,status,targetMinutes,featured,tags,script\r\n";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base;
    private String accessToken = "", refreshToken = "";

    CsvImporter(String base) { this.base = base.replaceAll("/+$", ""); }

    static int run(String[] args) throws Exception {
        if (args.length == 2 && "--csv-template".equals(args[0])) {
            Files.writeString(Path.of(args[1]), "\uFEFF" + HEADERS, StandardCharsets.UTF_8);
            System.out.println("Шаблон CSV сохранён: " + args[1]);
            return 0;
        }
        var options = Options.parse(args);
        if (Files.size(options.file()) > 5 * 1024 * 1024) throw new IllegalArgumentException("CSV не должен превышать 5 МБ.");
        byte[] data = Files.readAllBytes(options.file());
        if (data.length == 0) throw new IllegalArgumentException("CSV-файл пуст.");
        var importer = new CsvImporter(System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080"));
        var console = System.console();
        String login = System.getenv("IMPORT_LOGIN"), password = System.getenv("IMPORT_PASSWORD");
        if (login == null || login.isBlank()) {
            if (console == null) throw new IllegalArgumentException("Укажите IMPORT_LOGIN и IMPORT_PASSWORD в окружении консольного импортёра.");
            login = console.readLine("Логин редактора: ");
        }
        if (password == null || password.isEmpty()) {
            if (console == null) throw new IllegalArgumentException("Укажите IMPORT_PASSWORD в окружении либо запустите импортёр в терминале с интерактивным вводом.");
            char[] chars = console.readPassword("Пароль: ");
            if (chars == null) throw new IllegalArgumentException("Ввод отменён.");
            password = new String(chars); Arrays.fill(chars, '\0');
        }
        try {
            importer.session(importer.json("session/login", Map.of("login", login, "password", password)));
            var profile = importer.get("session/me");
            if (profile.path("mustChangePassword").asBoolean()) throw new IllegalArgumentException("Сначала смените временный пароль в профиле пользователя.");
            boolean editable = false;
            for (var role : profile.path("roles")) if (Set.of("ADMIN", "EDITOR").contains(role.asString())) editable = true;
            if (!editable) throw new IllegalArgumentException("Импорт доступен редактору или администратору.");
            var preview = importer.preview(data, options.mode());
            System.out.printf("Проверка завершена. Корректных строк: %d. Ошибок: %d.%n", preview.path("validCount").asInt(), preview.path("errorCount").asInt());
            for (var error : preview.path("errors")) System.out.printf("Строка %d: %s%n", error.path("row").asInt(), error.path("message").asString());
            if (!preview.path("canApply").asBoolean()) { System.out.println("Импорт не применён. Исправьте файл и повторите проверку."); return 2; }
            if (options.check()) { System.out.println("Режим проверки: изменения не внесены."); return 0; }
            if (!options.yes()) {
                if (console == null || !"да".equalsIgnoreCase(Objects.toString(console.readLine("Применить импорт? Введите «да»: "), "").trim())) {
                    System.out.println("Импорт отменён. Изменения не внесены. Для применения без вопроса используйте --yes."); return 0;
                }
            }
            // A user may review the preview for longer than the access token lifetime.
            importer.session(importer.json("session/refresh", Map.of("refreshToken", importer.refreshToken)));
            var result = importer.json("library/works/import/apply", Map.of("id", preview.path("id").asString()));
            System.out.printf("Импорт завершён. Обработано произведений: %d.%n", result.path("imported").asInt());
            return 0;
        } finally {
            if (!importer.refreshToken.isBlank()) {
                try { importer.json("session/logout", Map.of("refreshToken", importer.refreshToken)); }
                catch (Exception ignored) { System.err.println("Не удалось завершить сессию импортёра. Она завершится по сроку действия."); }
            }
        }
    }

    private void session(JsonNode session) { accessToken = session.path("accessToken").asString(); refreshToken = session.path("refreshToken").asString(); }
    private JsonNode get(String path) throws Exception { return send(path, "GET", null, "application/json"); }
    private JsonNode json(String path, Object value) throws Exception { return send(path, "POST", JSON.writeValueAsBytes(value), "application/json"); }
    private JsonNode preview(byte[] csv, String mode) throws Exception {
        String boundary = "TeremokImport" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"mode\"\r\n\r\n" + mode + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"import.csv\"\r\nContent-Type: text/csv;charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(csv);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send("library/works/import/preview", "POST", body.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }
    private JsonNode send(String path, String method, byte[] body, String type) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + "/api/v1/" + path)).timeout(Duration.ofMinutes(2)).header("Content-Type", type);
        if (!accessToken.isBlank()) request.header("Authorization", "Bearer " + accessToken);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<String> response;
        try { response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
        catch (java.io.IOException e) { throw new java.io.IOException("Нет связи с backend. Проверьте BACKEND_URL и запуск Docker-стенда."); }
        JsonNode result = response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalArgumentException("HTTP " + response.statusCode() + ": " + result.path("message").asString("Запрос отклонён сервером"));
        return result;
    }

    record Options(Path file, String mode, boolean check, boolean yes) {
        static Options parse(String[] args) {
            Path file = null; String mode = "NEW"; boolean check = false, yes = false;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--csv" -> { if (++i >= args.length) throw new IllegalArgumentException("После --csv укажите путь к файлу."); file = Path.of(args[i]); }
                    case "--mode" -> { if (++i >= args.length) throw new IllegalArgumentException("После --mode укажите NEW или UPSERT."); mode = args[i].toUpperCase(Locale.ROOT); }
                    case "--check" -> check = true;
                    case "--yes" -> yes = true;
                    default -> throw new IllegalArgumentException("Неизвестный параметр: " + args[i]);
                }
            }
            if (file == null || !Set.of("NEW", "UPSERT").contains(mode) || (check && yes)) throw new IllegalArgumentException("Использование: --csv файл.csv [--mode NEW|UPSERT] [--check | --yes]");
            return new Options(file, mode, check, yes);
        }
    }
}
