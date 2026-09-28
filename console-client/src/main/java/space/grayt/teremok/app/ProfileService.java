package space.grayt.teremok.app;

import java.util.List;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.ProfileRepository;

/** Profiles for the screens, so that the console does not talk to a repository directly. */
public final class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    public List<Profile> all() {
        return repository.findAll();
    }

    /** Throws IllegalArgumentException for an invalid or taken name, with a message for the user. */
    public Profile create(String name) {
        return repository.create(name);
    }

    /** Display name of a profile, or the id itself when there is no such profile. */
    public String nameOf(String profileId) {
        return repository.nameOf(profileId);
    }
}
