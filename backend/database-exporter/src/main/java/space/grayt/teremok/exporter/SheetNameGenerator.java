package space.grayt.teremok.exporter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

final class SheetNameGenerator {

    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private final Set<String> usedNames = new HashSet<>();

    String create(String prefix, String tableName) {
        var baseName = sanitize(prefix + "__" + tableName);
        if (baseName.length() > MAX_SHEET_NAME_LENGTH) {
            var suffix = "~" + shortHash(baseName);
            baseName = baseName.substring(0, MAX_SHEET_NAME_LENGTH - suffix.length()) + suffix;
        }

        var candidate = baseName;
        var index = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            var suffix = "~" + index++;
            candidate = baseName.substring(0, Math.min(baseName.length(), MAX_SHEET_NAME_LENGTH - suffix.length()))
                    + suffix;
        }
        return candidate;
    }

    private static String sanitize(String name) {
        var result = new StringBuilder(name.length());
        for (var index = 0; index < name.length(); index++) {
            var character = name.charAt(index);
            if (character < 0x20 || character == ':' || character == '\\' || character == '/'
                    || character == '?' || character == '*' || character == '[' || character == ']') {
                result.append('_');
            } else {
                result.append(character);
            }
        }
        if (!result.isEmpty() && result.charAt(0) == '\'') {
            result.setCharAt(0, '_');
        }
        if (!result.isEmpty() && result.charAt(result.length() - 1) == '\'') {
            result.setCharAt(result.length() - 1, '_');
        }
        return result.isEmpty() ? "sheet" : result.toString();
    }

    private static String shortHash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 3);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
