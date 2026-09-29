package space.grayt.teremok.cli;

import java.io.IOException;
import space.grayt.teremok.backend.BackendClient;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.exception.TeremokException;

/**
 * Main menu. Returns when the user quits or switches profile. A failed action, including a lost
 * database connection, is explained and the menu stays: the user can retry or quit.
 */
public final class MainMenu {

    public enum MenuExit {
        SWITCH_PROFILE,
        QUIT
    }

    private final Console console;
    private final ListenFlow listen;
    private final RecordFlow record;
    private final MyVoicingsScreen mine;
    private final BackendClient backend;

    public MainMenu(Console console, ListenFlow listen, RecordFlow record, MyVoicingsScreen mine) {
        this(console, listen, record, mine, BackendClient.fromEnvironment());
    }

    MainMenu(Console console, ListenFlow listen, RecordFlow record, MyVoicingsScreen mine,
            BackendClient backend) {
        this.console = console;
        this.listen = listen;
        this.record = record;
        this.mine = mine;
        this.backend = backend;
    }

    public MenuExit run(Profile profile) {
        while (true) {
            console.println();
            console.println("Теремок — " + profile.name());
            console.println();
            console.println("  1  Слушать произведение");
            console.println("  2  Озвучить произведение");
            console.println("  3  Мои озвучки");
            console.println("  4  Сменить профиль");
            console.println("  5  Количество записей в CatalogService");
            console.println("  6  Количество записей в TextWorkContentService");
            console.println("  0  Выход");

            String command = console.ask("> ");
            switch (command) {
                case "4" -> {
                    return MenuExit.SWITCH_PROFILE;
                }
                case "0" -> {
                    return MenuExit.QUIT;
                }
                default -> perform(command, profile);
            }
        }
    }

    private void perform(String command, Profile profile) {
        try {
            switch (command) {
                case "1" -> listen.run(profile);
                case "2" -> record.run(profile);
                case "3" -> mine.run(profile);
                case "5" -> printCatalogCount();
                case "6" -> printContentCount();
                default -> console.println("Не понимаю. Введите число от 0 до 6.");
            }
        } catch (TeremokException e) {
            console.println(e.getMessage());
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
}
