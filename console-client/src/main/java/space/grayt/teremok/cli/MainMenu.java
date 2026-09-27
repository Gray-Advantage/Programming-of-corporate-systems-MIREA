package space.grayt.teremok.cli;

import java.io.IOException;
import java.util.List;
import space.grayt.teremok.backend.BackendClient;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.VoicingRepository;

/** Main menu. Returns when the user quits or switches profile. */
public final class MainMenu {

    public enum MenuExit {
        SWITCH_PROFILE,
        QUIT
    }

    private final Console console;
    private final ListenFlow listen;
    private final RecordFlow record;
    private final MyVoicingsScreen mine;
    private final VoicingRepository voicings;
    private final BackendClient backend;
    // How many voicings.warnings() have been shown already: the list only grows (see
    // FileVoicingRepository), so printing the tail after that count is enough.
    private int shownWarnings;

    public MainMenu(Console console, ListenFlow listen, RecordFlow record, MyVoicingsScreen mine,
            VoicingRepository voicings) {
        this(console, listen, record, mine, voicings, BackendClient.fromEnvironment());
    }

    MainMenu(Console console, ListenFlow listen, RecordFlow record, MyVoicingsScreen mine,
            VoicingRepository voicings, BackendClient backend) {
        this.console = console;
        this.listen = listen;
        this.record = record;
        this.mine = mine;
        this.voicings = voicings;
        this.backend = backend;
    }

    public MenuExit run(Profile profile) {
        while (true) {
            showNewWarnings();
            console.println();
            console.println("Теремок — " + profile.name());
            console.println();
            console.println("  1  Слушать книгу");
            console.println("  2  Озвучить книгу");
            console.println("  3  Мои озвучки");
            console.println("  4  Сменить профиль");
            console.println("  5  Количество записей в CatalogService");
            console.println("  6  Количество записей в TextWorkContentService");
            console.println("  0  Выход");

            switch (console.ask("> ")) {
                case "1" -> listen.run(profile);
                case "2" -> record.run(profile);
                case "3" -> mine.run(profile);
                case "4" -> {
                    return MenuExit.SWITCH_PROFILE;
                }
                case "5" -> printCatalogCount();
                case "6" -> printContentCount();
                case "0" -> {
                    return MenuExit.QUIT;
                }
                default -> console.println("Не понимаю. Введите число от 0 до 6.");
            }
        }
    }

    private void printCatalogCount() {
        try {
            console.println("Записей в CatalogService: " + backend.catalogTextWorksCount());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            console.println("Запрос к CatalogService прерван.");
        } catch (IOException | IllegalArgumentException e) {
            console.println("CatalogService недоступен: " + e.getMessage());
        }
    }

    private void printContentCount() {
        try {
            console.println("Записей в TextWorkContentService: " + backend.contentTextWorksCount());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            console.println("Запрос к TextWorkContentService прерван.");
        } catch (IOException | IllegalArgumentException e) {
            console.println("TextWorkContentService недоступен: " + e.getMessage());
        }
    }

    /** Spec §8: damaged voicings and votes come with a warning, but not on every menu loop. */
    private void showNewWarnings() {
        List<String> warnings = voicings.warnings();
        if (shownWarnings >= warnings.size()) {
            return;
        }
        for (String warning : warnings.subList(shownWarnings, warnings.size())) {
            console.println(warning);
        }
        shownWarnings = warnings.size();
    }
}
