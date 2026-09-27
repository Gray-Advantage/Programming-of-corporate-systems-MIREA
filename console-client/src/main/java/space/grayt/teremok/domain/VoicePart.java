package space.grayt.teremok.domain;

import java.util.Locale;

public record VoicePart(String id, String name) {

    public static String idOf(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    public static VoicePart of(String name) {
        return new VoicePart(idOf(name), name.trim());
    }
}
