package space.grayt.teremok.cli;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.audio.RecordingSession;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Line;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.Speaker;
import space.grayt.teremok.domain.Voicing;

/** Озвучка персонажа: реплика за репликой, с немедленным сохранением каждой. */
public final class RecordFlow {

    private final Console console;
    private final BookLibrary books;
    private final VoicingService voicings;
    private final AudioPlayer player;

    public RecordFlow(Console console, BookLibrary books, VoicingService voicings, AudioPlayer player) {
        this.console = console;
        this.books = books;
        this.voicings = voicings;
        this.player = player;
    }

    public void run(Profile profile) {
        Optional<Book> book = chooseBook();
        if (book.isEmpty()) {
            return;
        }
        while (true) {
            Optional<Speaker> speaker = chooseSpeaker(book.get(), profile);
            if (speaker.isEmpty()) {
                return;
            }
            recordRole(book.get(), voicings.draftFor(book.get(), speaker.get().id(), profile.id()));
        }
    }

    /** Запись конкретной роли. Возвращает управление, когда пользователь вышел. */
    public void recordRole(Book book, Voicing voicing) {
        Voicing current = voicings.reload(voicing);
        while (true) {
            List<Line> missing = voicings.missingLines(current, book);
            Line target;
            if (missing.isEmpty()) {
                console.println("Все реплики записаны. Роль можно опубликовать в «Моих озвучках».");
                Optional<Line> chosen = chooseLine(book, current);
                if (chosen.isEmpty()) {
                    return;
                }
                target = chosen.get();
            } else {
                target = missing.get(0);
            }
            Optional<Voicing> next = recordLine(book, current, target);
            if (next.isEmpty()) {
                return;
            }
            current = next.get();
        }
    }

    private enum AfterRecording {
        NEXT,
        EXIT
    }

    /** Пустой результат — пользователь вышел из записи. */
    private Optional<Voicing> recordLine(Book book, Voicing voicing, Line startLine) {
        Line line = startLine;
        while (true) {
            List<Line> all = book.linesOf(voicing.speakerId());
            String speakerName = book.speaker(voicing.speakerId()).map(Speaker::name).orElse(voicing.speakerId());

            console.println();
            console.println(speakerName + " — реплика " + (all.indexOf(line) + 1) + " из " + all.size());
            console.println();
            console.println("  " + line.text());
            console.println();

            String command = console.ask("Enter — начать запись, s — список реплик, 0 — выйти: ");
            if (command.equals("0")) {
                return Optional.empty();
            }
            if (command.equals("s")) {
                Optional<Line> chosen = chooseLine(book, voicings.reload(voicing));
                if (chosen.isEmpty()) {
                    return Optional.empty();
                }
                line = chosen.get();
                continue;
            }
            if (!record(voicing, line)) {
                return Optional.empty();
            }
            if (afterRecording(voicing, line) == AfterRecording.EXIT) {
                return Optional.empty();
            }
            return Optional.of(voicings.reload(voicing));
        }
    }

    private AfterRecording afterRecording(Voicing voicing, Line line) {
        while (true) {
            switch (console.ask("1 прослушать, 2 перезаписать, 3 дальше, 0 выйти: ")) {
                case "1" -> playRecorded(voicing, line);
                case "2" -> {
                    if (!record(voicing, line)) {
                        return AfterRecording.EXIT;
                    }
                }
                case "3" -> {
                    return AfterRecording.NEXT;
                }
                case "0" -> {
                    return AfterRecording.EXIT;
                }
                default -> console.println("Не понимаю. Введите 1, 2, 3 или 0.");
            }
        }
    }

    /** Список всех реплик персонажа с отметками — отсюда перезаписывают уже готовую. */
    private Optional<Line> chooseLine(Book book, Voicing voicing) {
        List<Line> all = book.linesOf(voicing.speakerId());
        console.println();
        console.println("Реплики персонажа:");
        console.println();
        for (int i = 0; i < all.size(); i++) {
            Line line = all.get(i);
            String mark = voicing.isRecorded(line.number()) ? "готово" : "пусто ";
            console.println("  " + (i + 1) + "  [" + mark + "] " + line.text());
        }
        console.println("  0  Назад");
        return pick(all);
    }

    /** false означает, что записать не удалось и нужно выйти из роли. */
    private boolean record(Voicing voicing, Line line) {
        try {
            RecordingSession session = voicings.startRecording(voicing, line.number());
            console.println("● запись идёт, максимум 2 минуты");
            console.ask("Enter — стоп: ");
            boolean stoppedByLimit = !session.isRecording();
            session.stop();
            if (stoppedByLimit) {
                console.println("Запись остановлена по лимиту в 2 минуты.");
            }
            return true;
        } catch (AudioUnavailableException e) {
            console.println(e.getMessage());
            return false;
        }
    }

    private void playRecorded(Voicing voicing, Line line) {
        try {
            player.play(voicings.audioFile(voicing, line.number()));
        } catch (AudioUnavailableException e) {
            console.println(e.getMessage());
        }
    }

    private Optional<Book> chooseBook() {
        List<Book> all = books.all();
        if (all.isEmpty()) {
            console.println("Нет ни одной книги.");
            return Optional.empty();
        }
        console.println();
        console.println("Какую книгу озвучиваем?");
        console.println();
        for (int i = 0; i < all.size(); i++) {
            Book book = all.get(i);
            console.println("  " + (i + 1) + "  " + book.title() + " — " + Plural.lines(book.lines().size()));
        }
        console.println("  0  Назад");
        return pick(all);
    }

    private Optional<Speaker> chooseSpeaker(Book book, Profile profile) {
        List<Speaker> speakers = book.speakers();
        console.println();
        console.println(book.title() + " — кого озвучиваем?");
        console.println();
        for (int i = 0; i < speakers.size(); i++) {
            Speaker speaker = speakers.get(i);
            int recorded = voicings.recordedCountFor(book, speaker.id(), profile.id());
            int total = book.linesOf(speaker.id()).size();
            String progress = recorded == 0 ? "не начато" : recorded + "/" + total;
            console.println("  " + (i + 1) + "  " + speaker.name() + " — " + Plural.lines(total) + ", " + progress);
        }
        console.println("  0  Назад");
        return pick(speakers);
    }

    private <T> Optional<T> pick(List<T> items) {
        while (true) {
            String command = console.ask("> ");
            if (command.equals("0")) {
                return Optional.empty();
            }
            OptionalInt index = Console.index(command, items.size());
            if (index.isPresent()) {
                return Optional.of(items.get(index.getAsInt()));
            }
            console.println("Не понимаю. Введите номер из списка или 0.");
        }
    }
}
