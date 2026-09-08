package space.grayt.teremok.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.grayt.teremok.domain.Profile;

/** Профили в файле data/profiles.txt строками вида «id=Имя». */
public final class FileProfileRepository implements ProfileRepository {

    private final Path file;

    public FileProfileRepository(Path dataDir) {
        this.file = dataDir.resolve("profiles.txt");
    }

    @Override
    public List<Profile> findAll() {
        return AtomicTextFile.readProperties(file).entrySet().stream()
                .map(entry -> new Profile(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public Optional<Profile> findById(String id) {
        return findAll().stream().filter(profile -> profile.id().equals(id)).findFirst();
    }

    @Override
    public Profile create(String name) {
        Profile profile = Profile.of(name);
        Map<String, String> all = AtomicTextFile.readProperties(file);
        if (all.containsKey(profile.id())) {
            throw new IllegalArgumentException("Профиль с таким именем уже есть: " + all.get(profile.id()));
        }
        all.put(profile.id(), profile.name());
        AtomicTextFile.writeProperties(file, all);
        return profile;
    }
}
