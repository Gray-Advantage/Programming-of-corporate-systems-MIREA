package space.grayt.teremok.db;

import space.grayt.teremok.config.Environment;

/** Where the database is and how to sign in. */
public record DatabaseConfig(String url, String user, String password) {

    public static DatabaseConfig from(Environment environment) {
        String url = environment.find("DB_URL").orElseGet(() -> "jdbc:postgresql://"
                + environment.get("POSTGRES_HOST", "localhost") + ":"
                + environment.get("POSTGRES_PORT", "5432") + "/"
                + environment.get("POSTGRES_DB", "teremok"));
        return new DatabaseConfig(url,
                environment.get("POSTGRES_USER", "teremok"),
                environment.get("POSTGRES_PASSWORD", "teremok"));
    }

    /** The URL for messages: a password passed as a URL parameter is hidden. */
    public String displayUrl() {
        return url.replaceAll("(?i)(password=)[^&;]*", "$1***");
    }

    @Override
    public String toString() {
        return "DatabaseConfig[url=" + displayUrl() + ", user=" + user + "]";
    }
}
