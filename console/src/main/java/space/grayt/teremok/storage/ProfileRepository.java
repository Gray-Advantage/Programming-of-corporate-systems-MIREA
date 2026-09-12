package space.grayt.teremok.storage;

import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.Profile;

public interface ProfileRepository {

    List<Profile> findAll();

    Optional<Profile> findById(String id);

    /** Создаёт профиль. Бросает IllegalArgumentException при плохом имени или дубле. */
    Profile create(String name);
}
