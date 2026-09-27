package space.grayt.teremok.backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse;
import space.grayt.teremok.client.contract.CountResponse;
import space.grayt.teremok.client.contract.TextWorkContentResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class BackendClient {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final TypeReference<List<CatalogTextWorkResponse>> CATALOG_LIST = new TypeReference<>() {
    };

    private final HttpClient client;
    private final URI catalogUri;
    private final URI contentUri;
    private final URI catalogCountUri;
    private final URI contentCountUri;

    public BackendClient(String baseUrl) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String normalizedBaseUrl = withoutTrailingSlash(baseUrl);
        this.catalogUri = URI.create(normalizedBaseUrl + "/api/v1/catalog/text-works");
        this.contentUri = URI.create(normalizedBaseUrl + "/api/v1/text-work-content/text-works");
        this.catalogCountUri = URI.create(catalogUri + "/count");
        this.contentCountUri = URI.create(contentUri + "/count");
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

    public List<CatalogTextWorkResponse> catalogTextWorks() throws IOException, InterruptedException {
        return request(catalogUri, CATALOG_LIST);
    }

    public CatalogTextWorkResponse catalogTextWork(UUID id) throws IOException, InterruptedException {
        return request(URI.create(catalogUri + "/" + id), CatalogTextWorkResponse.class);
    }

    public TextWorkContentResponse textWorkContent(UUID id) throws IOException, InterruptedException {
        return request(URI.create(contentUri + "/" + id), TextWorkContentResponse.class);
    }

    private long requestCount(URI uri) throws IOException, InterruptedException {
        return request(uri, CountResponse.class).count();
    }

    private <T> T request(URI uri, Class<T> responseType) throws IOException, InterruptedException {
        return JSON.readValue(requestBody(uri), responseType);
    }

    private <T> T request(URI uri, TypeReference<T> responseType) throws IOException, InterruptedException {
        return JSON.readValue(requestBody(uri), responseType);
    }

    private String requestBody(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
