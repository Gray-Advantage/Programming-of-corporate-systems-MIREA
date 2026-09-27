package space.grayt.teremok.storage;

import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.Profile;

public interface ProfileRepository {

    List<Profile> findAll();

    Optional<Profile> findById(String id);

    /** Creates a profile. Throws IllegalArgumentException for an invalid or duplicate name. */
    Profile create(String name);

    /** Display name of a profile, or the id itself when there is no such profile. */
    default String nameOf(String id) {
        return findById(id).map(Profile::name).orElse(id);
    }
}
