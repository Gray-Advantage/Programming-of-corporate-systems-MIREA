package space.grayt.teremok.auth.repository;

import java.util.Optional;
import space.grayt.teremok.auth.domain.UserAccount;

public interface UserAccountRepository {

    void save(UserAccount user);

    Optional<UserAccount> findByLogin(String login);
}
