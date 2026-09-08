package space.grayt.teremok.cli;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.ProfileRepository;

/** Выбор профиля при старте и при смене пользователя. */
public final class ProfileScreen {

    private final Console console;
    private final ProfileRepository profiles;

    public ProfileScreen(Console console, ProfileRepository profiles) {
        this.console = console;
        this.profiles = profiles;
    }

    /** Пустой результат означает выход из приложения. */
    public Optional<Profile> choose() {
        while (true) {
            List<Profile> all = profiles.findAll();
            console.println();
            console.println("Кто вы?");
            console.println();
            for (int i = 0; i < all.size(); i++) {
                console.println("  " + (i + 1) + "  " + all.get(i).name());
            }
            console.println("  n  Создать профиль");
            console.println("  0  Выход");

            String command = console.ask("> ");
            if (command.equals("0")) {
                return Optional.empty();
            }
            if (command.equalsIgnoreCase("n")) {
                Optional<Profile> created = create();
                if (created.isPresent()) {
                    return created;
                }
                continue;
            }
            OptionalInt index = Console.index(command, all.size());
            if (index.isPresent()) {
                return Optional.of(all.get(index.getAsInt()));
            }
            console.println("Не понимаю. Введите номер профиля, n или 0.");
        }
    }

    private Optional<Profile> create() {
        String name = console.ask("Имя профиля: ");
        if (console.isClosed()) {
            return Optional.empty();
        }
        try {
            return Optional.of(profiles.create(name));
        } catch (IllegalArgumentException e) {
            console.println(e.getMessage());
            return Optional.empty();
        }
    }
}
