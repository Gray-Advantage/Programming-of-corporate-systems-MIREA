package space.grayt.teremok.storage;

import static space.grayt.teremok.db.Jdbc.failure;
import static space.grayt.teremok.db.Jdbc.isConstraintViolation;
import static space.grayt.teremok.db.Jdbc.isUniqueViolation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.domain.Profile;

/** Profiles in the profiles table. */
public final class JdbcProfileRepository implements ProfileRepository {

    private static final String SELECT = "SELECT id, name FROM profiles";

    private final DatabaseManager database;

    public JdbcProfileRepository(DatabaseManager database) {
        this.database = database;
    }

    /** In order of creation, so a new profile appears at the end of the list. */
    @Override
    public List<Profile> findAll() {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(SELECT + " ORDER BY created_at, id");
             ResultSet rows = statement.executeQuery()) {
            List<Profile> profiles = new ArrayList<>();
            while (rows.next()) {
                profiles.add(profile(rows));
            }
            return profiles;
        } catch (SQLException e) {
            throw failure("Не удалось прочитать профили", e);
        }
    }

    @Override
    public Optional<Profile> findById(String id) {
        try (Connection connection = database.connect()) {
            return findById(connection, id);
        } catch (SQLException e) {
            throw failure("Не удалось прочитать профиль " + id, e);
        }
    }

    @Override
    public Profile create(String name) {
        Profile profile = Profile.of(name);
        try (Connection connection = database.connect()) {
            Optional<Profile> existing = findById(connection, profile.id());
            if (existing.isPresent()) {
                throw duplicate(existing.get().name());
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO profiles (id, name) VALUES (?, ?)")) {
                statement.setString(1, profile.id());
                statement.setString(2, profile.name());
                statement.executeUpdate();
            }
            return profile;
        } catch (SQLException e) {
            // The primary key catches a profile created by another client between the check and the insert.
            if (isUniqueViolation(e)) {
                throw duplicate(profile.name());
            }
            if (isConstraintViolation(e)) {
                throw new IllegalArgumentException("База данных не принимает имя профиля " + profile.name(), e);
            }
            throw failure("Не удалось создать профиль " + profile.name(), e);
        }
    }

    private static Optional<Profile> findById(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(profile(rows)) : Optional.empty();
            }
        }
    }

    private static Profile profile(ResultSet row) throws SQLException {
        return new Profile(row.getString("id"), row.getString("name"));
    }

    private static IllegalArgumentException duplicate(String name) {
        return new IllegalArgumentException("Профиль с таким именем уже есть: " + name);
    }
}
