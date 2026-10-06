package space.grayt.teremok.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import space.grayt.teremok.auth.domain.UserAccount;

@Repository
public class PostgresUserAccountRepository implements UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(UserAccount user) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (id, login, password_hash, registered_at)
                VALUES (?, ?, ?, ?)
                """,
                user.id(),
                user.login(),
                user.passwordHash(),
                user.registeredAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public Optional<UserAccount> findByLogin(String login) {
        return jdbcTemplate.query(
                        """
                        SELECT id, login, password_hash, registered_at
                        FROM user_account
                        WHERE login = ?
                        """,
                        this::mapUser,
                        login)
                .stream()
                .findFirst();
    }

    private UserAccount mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserAccount(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("login"),
                resultSet.getString("password_hash"),
                resultSet.getObject("registered_at", OffsetDateTime.class).toInstant());
    }
}
