package space.grayt.teremok.fx.net;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import space.grayt.teremok.client.contract.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** All database and object-storage access goes through backend HTTP, never JDBC. */
public final class RestApi {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;
    private volatile AuthTokenResponse session;
    private String login;

    public RestApi(String address) {
        URI uri = URI.create(address.trim());
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Укажите HTTP-адрес backend, например http://localhost:8080");
        }
        base = address.trim().replaceAll("/+$", "");
    }

    public String login() { return login; }
    public UUID userId() { return session.userId(); }
    public String address() { return base; }
    public void authenticate(String login, String password, boolean register) throws Exception {
        if (login.isBlank() || password.isBlank()) throw new IllegalArgumentException("Введите логин и пароль");
        var result = read(send("POST", "/api/v1/auth/" + (register ? "register" : "login"),
                new AuthCredentialsRequest(login.trim(), password)), AuthTokenResponse.class);
        this.login = login.trim();
        session = result;
    }

    public void logout() throws Exception {
        try { send("POST", "/api/v1/auth/logout", null); }
        finally { session = null; login = null; }
    }

    public List<CatalogTextWorkResponse> catalog() throws Exception {
        return JSON.readValue(send("GET", "/api/v1/catalog/text-works", null),
                new TypeReference<List<CatalogTextWorkResponse>>() {});
    }
    public TextWorkContentResponse content(UUID id) throws Exception {
        return read(send("GET", "/api/v1/text-work-content/text-works/" + id, null), TextWorkContentResponse.class);
    }
    public RoleDraftsResponse drafts(UUID role) throws Exception {
        return read(send("GET", "/api/v1/draft-recordings/roles/" + role, null), RoleDraftsResponse.class);
    }
    public DraftRecordingResponse upload(UUID fragment, Path path) throws Exception {
        long size = Files.size(path);
        if (size == 0 || size > 100L * 1024 * 1024) throw new IllegalArgumentException("Размер аудио должен быть от 1 байта до 100 МБ");
        String boundary = "teremok-" + UUID.randomUUID();
        String name = path.getFileName().toString().replace("\r", "_").replace("\n", "_").replace("\"", "_").replace("\\", "_");
        String type = Optional.ofNullable(Files.probeContentType(path)).orElse("application/octet-stream");
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        var publisher = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                HttpRequest.BodyPublishers.ofFile(path),
                HttpRequest.BodyPublishers.ofByteArray(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8)));
        var request = request("/api/v1/draft-recordings/fragments/" + fragment)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(publisher).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        check(response.statusCode(), response.body());
        return read(response.body(), DraftRecordingResponse.class);
    }
    public void deleteDraft(UUID id) throws Exception { send("DELETE", "/api/v1/draft-recordings/" + id, null); }
    public PublishedRoleRecordingResponse publish(UUID role, List<PublishRoleRecordingRequest.Selection> selections) throws Exception {
        return read(send("POST", "/api/v1/draft-recordings/roles/" + role + "/publish",
                new PublishRoleRecordingRequest(selections)), PublishedRoleRecordingResponse.class);
    }
    public PublishedRolesResponse published(UUID work) throws Exception {
        return read(send("GET", "/api/v1/recordings/text-works/" + work + "/roles", null), PublishedRolesResponse.class);
    }
    public RenderJobResponse render(CreateRenderRequest body) throws Exception {
        return read(send("POST", "/api/v1/recordings/renders", body), RenderJobResponse.class);
    }
    public RenderJobResponse job(UUID id) throws Exception {
        return read(send("GET", "/api/v1/recordings/renders/" + id, null), RenderJobResponse.class);
    }
    public Statistics statistics() throws Exception {
        return read(send("GET", "/api/v1/export/statistics", null), Statistics.class);
    }
    public DownloadResult export(List<String> services, Path target) throws Exception {
        if (services.isEmpty()) throw new IllegalArgumentException("Выберите хотя бы один сервис");
        return download("/api/v1/export/xlsx?" + services.stream()
                .map(s -> "services=" + URLEncoder.encode(s, StandardCharsets.UTF_8)).reduce((a,c) -> a + "&" + c).orElseThrow(), target);
    }
    public DownloadResult draftAudio(UUID id, Path target) throws Exception {
        return download("/api/v1/draft-recordings/" + id + "/audio", target);
    }
    public DownloadResult output(UUID id, int index, Path target) throws Exception {
        return download("/api/v1/recordings/renders/" + id + "/outputs/" + index, target);
    }

    private HttpRequest.Builder request(String path) throws ApiException {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofMinutes(3));
        var token = session;
        if (token != null) {
            if (!token.expiresAt().isAfter(Instant.now())) throw new ApiException(401, "Срок входа истёк. Войдите заново");
            builder.header("Authorization", "Bearer " + token.accessToken());
        }
        return builder;
    }
    private byte[] send(String method, String path, Object body) throws Exception {
        var builder = request(path);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body));
        if (body != null) builder.header("Content-Type", "application/json");
        var response = http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofByteArray());
        check(response.statusCode(), response.body());
        return response.body();
    }
    private DownloadResult download(String path, Path target) throws Exception {
        var response = http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        Path temporary = null;
        try (var input = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) check(response.statusCode(), input.readNBytes(8192));
            temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".teremok-download-", ".tmp");
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            return new DownloadResult(target, response.headers().firstValue("X-Export-Failed-Tables").orElse("0"));
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }
    private static <T> T read(byte[] body, Class<T> type) { return JSON.readValue(body, type); }
    private static void check(int status, byte[] body) throws ApiException {
        if (status >= 200 && status < 300) return;
        String detail = "";
        try { detail = JSON.readTree(body).path("message").asString(""); } catch (RuntimeException ignored) { /* Not every gateway response is JSON. */ }
        String message = switch (status) {
            case 401 -> "Неверный логин или пароль, либо срок входа истёк. Войдите заново.";
            case 403 -> "Это действие недоступно для вашей учётной записи.";
            case 404 -> "Данные пока недоступны. Обновите список; новые события обрабатываются не сразу.";
            case 409 -> "Такая запись уже существует. Для регистрации выберите другой логин.";
            case 413 -> "Аудиофайл слишком большой. Максимум — 100 МБ.";
            default -> status >= 500 ? "Backend временно недоступен. Проверьте Docker и повторите действие."
                    : "Запрос отклонён (HTTP " + status + "). " + detail;
        };
        throw new ApiException(status, message);
    }
    public record Metric(String title, Long value, String error) {}
    public record Statistics(Instant updatedAt, List<Metric> metrics) {}
    public record DownloadResult(Path path, String failedTables) {}
}
