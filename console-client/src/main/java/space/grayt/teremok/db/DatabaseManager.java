package space.grayt.teremok.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import space.grayt.teremok.storage.StorageException;

/**
 * Opens JDBC connections. Every repository operation takes its own connection and closes it in
 * try-with-resources: the console makes a few queries per screen, so a pool would add a dependency
 * without a noticeable gain.
 */
public final class DatabaseManager {

    private final DatabaseConfig config;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    public String displayUrl() {
        return config.displayUrl();
    }

    /** Called once at startup, so that a stopped container is explained before the first screen. */
    public void check() {
        try (Connection connection = connect()) {
            checkSchema(connection);
        } catch (SQLException e) {
            throw new StorageException("Не удалось подключиться к базе данных " + displayUrl() + ".\n"
                    + "Причина: " + e.getMessage() + "\n"
                    + "Запустите PostgreSQL из корня репозитория: docker compose up -d postgres", e);
        }
    }

    private void checkSchema(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM voicings");
             ResultSet result = statement.executeQuery()) {
            result.next();
        } catch (SQLException e) {
            throw new StorageException("База данных " + displayUrl() + " доступна, но в ней нет таблиц Теремка.\n"
                    + "Причина: " + e.getMessage() + "\n"
                    + "Схему создаёт database/schema.sql при первом запуске контейнера; "
                    + "как пересоздать базу — в console-client/README.md.", e);
        }
    }
}
