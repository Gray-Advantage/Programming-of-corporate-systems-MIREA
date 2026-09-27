package space.grayt.teremok.textwork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.VoicePart;

/** Parses the bundled fixture format: header, ---, then lines in the form Name: text. */
public final class TextWorkParser {

    private static final String SEPARATOR = "---";
    private static final int MAX_FRAGMENTS = 9999;

    private TextWorkParser() {
    }

    public static TextWork parse(String textWorkId, String source) {
        List<String> raw = source.lines().toList();
        int separatorAt = raw.indexOf(SEPARATOR);
        if (separatorAt < 0) {
            throw new TextWorkFormatException("В произведении нет строки-разделителя ---", raw.size());
        }

        String title = title(raw.subList(0, separatorAt));
        List<TextWorkFragment> fragments = new ArrayList<>();
        Map<String, VoicePart> voiceParts = new LinkedHashMap<>();

        for (int i = separatorAt + 1; i < raw.size(); i++) {
            String sourceLine = raw.get(i);
            if (sourceLine.isBlank()) {
                continue;
            }
            int colon = sourceLine.indexOf(':');
            if (colon <= 0) {
                throw new TextWorkFormatException(
                        "Фрагмент должен быть в виде «Название роли: текст»", i + 1);
            }
            VoicePart voicePart = VoicePart.of(sourceLine.substring(0, colon));
            voiceParts.putIfAbsent(voicePart.id(), voicePart);
            fragments.add(new TextWorkFragment(
                    fragments.size() + 1,
                    voicePart.id(),
                    sourceLine.substring(colon + 1).trim()));
            if (fragments.size() > MAX_FRAGMENTS) {
                throw new TextWorkFormatException(
                        "В произведении больше " + MAX_FRAGMENTS + " фрагментов", i + 1);
            }
        }

        if (fragments.isEmpty()) {
            throw new TextWorkFormatException("В произведении нет ни одного фрагмента", raw.size());
        }
        return new TextWork(textWorkId, title, fragments, voiceParts);
    }

    private static String title(List<String> header) {
        for (int i = 0; i < header.size(); i++) {
            String sourceLine = header.get(i);
            if (sourceLine.isBlank()) {
                continue;
            }
            int colon = sourceLine.indexOf(':');
            if (colon <= 0) {
                throw new TextWorkFormatException("Заголовок должен быть в виде «ключ: значение»", i + 1);
            }
            if (sourceLine.substring(0, colon).trim().equals("title")) {
                String title = sourceLine.substring(colon + 1).trim();
                if (title.isEmpty()) {
                    throw new TextWorkFormatException("Пустой title у произведения", i + 1);
                }
                return title;
            }
        }
        throw new TextWorkFormatException("В заголовке произведения нет поля title", 1);
    }
}
