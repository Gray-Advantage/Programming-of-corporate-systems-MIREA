package space.grayt.teremok.cli;

import java.util.List;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.VoicingRepository;

/** Главное меню. Возвращает управление, когда пользователь выходит или меняет профиль. */
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
    // Сколько предупреждений voicings.warnings() уже показано: список только растёт (см.
    // FileVoicingRepository), поэтому достаточно печатать хвост после уже показанного количества.
    private int shownWarnings;

    public MainMenu(Console console, ListenFlow listen, RecordFlow record, MyVoicingsScreen mine,
            VoicingRepository voicings) {
        this.console = console;
        this.listen = listen;
        this.record = record;
        this.mine = mine;
        this.voicings = voicings;
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
            console.println("  0  Выход");

            switch (console.ask("> ")) {
                case "1" -> listen.run(profile);
                case "2" -> record.run(profile);
                case "3" -> mine.run(profile);
                case "4" -> {
                    return MenuExit.SWITCH_PROFILE;
                }
                case "0" -> {
                    return MenuExit.QUIT;
                }
                default -> console.println("Не понимаю. Введите число от 0 до 4.");
            }
        }
    }

    /** §8: повреждённые роли и голоса сопровождаются предупреждением, но не на каждом витке меню. */
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
