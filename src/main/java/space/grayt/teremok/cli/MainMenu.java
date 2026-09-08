package space.grayt.teremok.cli;

import space.grayt.teremok.domain.Profile;

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

    public MainMenu(Console console, ListenFlow listen, RecordFlow record, MyVoicingsScreen mine) {
        this.console = console;
        this.listen = listen;
        this.record = record;
        this.mine = mine;
    }

    public MenuExit run(Profile profile) {
        while (true) {
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
}
