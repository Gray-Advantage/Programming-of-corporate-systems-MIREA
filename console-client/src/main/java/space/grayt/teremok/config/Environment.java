package space.grayt.teremok.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Settings from environment variables, falling back to the nearest .env file. docker compose reads
 * the .env in the repository root, while ./gradlew :console-client:run starts the client in
 * console-client/, so the file is looked up in the working directory and up to three parents:
 * one .env then configures both.
 */
public final class Environment {

    static final String DOT_ENV = ".env";
    static final int MAX_PARENTS = 3;

    private final Map<String, String> variables;
    private final Map<String, String> dotEnv;

    private Environment(Map<String, String> variables, Map<String, String> dotEnv) {
        this.variables = Map.copyOf(variables);
        this.dotEnv = Map.copyOf(dotEnv);
    }

    public static Environment load() {
        return of(System.getenv(), Path.of("").toAbsolutePath());
    }

    public static Environment of(Map<String, String> variables, Path workingDir) {
        Map<String, String> dotEnv = findDotEnv(workingDir).map(Environment::read).orElse(Map.of());
        return new Environment(variables, dotEnv);
    }

    /** A blank value counts as unset, the same as in docker compose. */
    public String get(String key, String fallback) {
        return find(key).orElse(fallback);
    }

    public Optional<String> find(String key) {
        String value = variables.get(key);
        if (value == null || value.isBlank()) {
            value = dotEnv.get(key);
        }
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    static Optional<Path> findDotEnv(Path workingDir) {
        Path dir = workingDir.toAbsolutePath().normalize();
        for (int level = 0; level <= MAX_PARENTS && dir != null; level++) {
            Path candidate = dir.resolve(DOT_ENV);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    /** KEY=VALUE lines; # starts a comment, quotes around a value are dropped. */
    static Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(line.substring(0, separator).strip(), value(line.substring(separator + 1).strip()));
        }
        return values;
    }

    private static String value(String raw) {
        if (raw.length() >= 2 && (raw.charAt(0) == '"' || raw.charAt(0) == '\'')
                && raw.charAt(raw.length() - 1) == raw.charAt(0)) {
            return raw.substring(1, raw.length() - 1);
        }
        int comment = raw.indexOf(" #");
        return comment >= 0 ? raw.substring(0, comment).strip() : raw;
    }

    /** An unreadable .env is treated as absent: the defaults still let the client start. */
    private static Map<String, String> read(Path file) {
        try {
            return parse(Files.readAllLines(file, UTF_8));
        } catch (IOException e) {
            return Map.of();
        }
    }
}
