package space.grayt.teremok.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import space.grayt.teremok.storage.StorageException;

/** Small conversions shared by the JDBC repositories. */
public final class Jdbc {

    private Jdbc() {
    }

    /** SQLSTATE class 23 is a constraint violation; PostgreSQL and H2 agree on the class. */
    public static boolean isConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }

    public static boolean isUniqueViolation(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    public static StorageException failure(String action, SQLException e) {
        return new StorageException(action + ": " + e.getMessage(), e);
    }

    /** UUID columns need a UUID parameter: PostgreSQL does not compare uuid with varchar. */
    public static UUID uuid(String id) {
        return UUID.fromString(id);
    }

    /** OffsetDateTime rather than Instant: it is the JDBC 4.2 type of TIMESTAMP WITH TIME ZONE. */
    public static OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
