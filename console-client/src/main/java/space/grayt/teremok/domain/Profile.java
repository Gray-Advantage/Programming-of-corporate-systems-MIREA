package space.grayt.teremok.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** An application user. The id is the lowercase name. */
public record Profile(String id, String name) {

    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N}_-]{1,24}$");

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name.trim()).matches();
    }

    public static Profile of(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!isValidName(trimmed)) {
            throw new IllegalArgumentException(
                    "Имя профиля: от 1 до 24 символов, только буквы, цифры, дефис и подчёркивание");
        }
        return new Profile(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }
}
