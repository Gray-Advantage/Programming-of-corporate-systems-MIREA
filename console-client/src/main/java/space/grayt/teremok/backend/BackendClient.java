package space.grayt.teremok.backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BackendClient {

    private static final Pattern COUNT = Pattern.compile("\\\"count\\\"\\s*:\\s*(\\d+)");

    private final HttpClient client;
    private final URI catalogCountUri;
    private final URI contentCountUri;

    public BackendClient(String baseUrl) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String normalizedBaseUrl = withoutTrailingSlash(baseUrl);
        this.catalogCountUri = URI.create(normalizedBaseUrl + "/api/v1/catalog/text-works/count");
        this.contentCountUri = URI.create(normalizedBaseUrl + "/api/v1/text-work-content/text-works/count");
    }

    public static BackendClient fromEnvironment() {
        return new BackendClient(System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080"));
    }

    public long catalogTextWorksCount() throws IOException, InterruptedException {
        return requestCount(catalogCountUri);
    }

    public long contentTextWorksCount() throws IOException, InterruptedException {
        return requestCount(contentCountUri);
    }

    private long requestCount(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        Matcher matcher = COUNT.matcher(response.body());
        if (!matcher.find()) {
            throw new IOException("В ответе backend нет поля count: " + response.body());
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
