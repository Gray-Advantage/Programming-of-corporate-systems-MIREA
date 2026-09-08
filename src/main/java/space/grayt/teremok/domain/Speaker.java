package space.grayt.teremok.domain;

import java.util.Locale;

/** Персонаж книги. Называется Speaker, потому что Character занят java.lang. */
public record Speaker(String id, String name) {

    public static String idOf(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    public static Speaker of(String name) {
        return new Speaker(idOf(name), name.trim());
    }
}
