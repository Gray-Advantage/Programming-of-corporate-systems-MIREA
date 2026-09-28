package space.grayt.teremok.db;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import space.grayt.teremok.storage.StorageException;

class DatabaseManagerTest {

    @Test
    void checkPassesOnDatabaseWithSchema() {
        assertDoesNotThrow(() -> TestDatabase.empty().check());
    }

    /** Port 1 is closed, so the connection is refused at once, as with a stopped container. */
    @Test
    void unreachableDatabaseIsExplainedWithUrlAndHint() {
        DatabaseManager database = new DatabaseManager(
                new DatabaseConfig("jdbc:postgresql://localhost:1/teremok", "teremok", "teremok"));

        StorageException error = assertThrows(StorageException.class, database::check);

        assertTrue(error.getMessage().contains("jdbc:postgresql://localhost:1/teremok"), error::getMessage);
        assertTrue(error.getMessage().contains("docker compose up -d postgres"), error::getMessage);
    }

    @Test
    void databaseWithoutSchemaIsExplained() {
        DatabaseManager database = new DatabaseManager(TestDatabase.config());

        StorageException error = assertThrows(StorageException.class, database::check);

        assertTrue(error.getMessage().contains("нет таблиц"), error::getMessage);
    }
}
