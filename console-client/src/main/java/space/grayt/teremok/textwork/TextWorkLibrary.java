package space.grayt.teremok.textwork;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.grayt.teremok.domain.TextWork;

/** Bundled text works kept as fixtures for the legacy console tests. */
public final class TextWorkLibrary implements TextWorkCatalog {

    public static final List<String> TEXT_WORK_IDS = List.of("shapochka", "teremok", "kolobok");

    private final Map<String, TextWork> textWorks = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public TextWorkLibrary() {
        for (String id : TEXT_WORK_IDS) {
            try {
                textWorks.put(id, TextWorkParser.parse(id, read(id)));
            } catch (TextWorkFormatException exception) {
                warnings.add("Произведение " + id + " пропущено, строка "
                        + exception.lineNumber() + ": " + exception.getMessage());
            } catch (IOException exception) {
                warnings.add("Произведение " + id + " не прочитано: " + exception.getMessage());
            }
        }
    }

    @Override
    public List<TextWork> all() {
        return List.copyOf(textWorks.values());
    }

    @Override
    public Optional<TextWork> find(String textWorkId) {
        return Optional.ofNullable(textWorks.get(textWorkId));
    }

    @Override
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private static String read(String textWorkId) throws IOException {
        String resource = "/text-works/" + textWorkId + ".txt";
        try (InputStream stream = TextWorkLibrary.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("ресурс " + resource + " не найден");
            }
            return new String(stream.readAllBytes(), UTF_8);
        }
    }
}
