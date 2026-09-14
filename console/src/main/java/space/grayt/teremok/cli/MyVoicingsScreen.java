package space.grayt.teremok.cli;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.Speaker;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;

/** Список собственных ролей и действия над ними. */
public final class MyVoicingsScreen {

    private final Console console;
    private final BookLibrary books;
    private final VoicingService voicings;
    private final VotingService voting;
    private final CastBuilder castBuilder;
    private final PlaybackService playback;
    private final PlaybackConsole playbackConsole;
    private final RecordFlow record;

    public MyVoicingsScreen(Console console, BookLibrary books, VoicingService voicings, VotingService voting,
                            CastBuilder castBuilder, PlaybackService playback, PlaybackConsole playbackConsole,
                            RecordFlow record) {
        this.console = console;
        this.books = books;
        this.voicings = voicings;
        this.voting = voting;
        this.castBuilder = castBuilder;
        this.playback = playback;
        this.playbackConsole = playbackConsole;
        this.record = record;
    }

    public void run(Profile profile) {
        while (true) {
            List<Voicing> mine = voicings.byAuthor(profile.id());
            console.println();
            console.println("Мои озвучки");
            console.println();
            if (mine.isEmpty()) {
                console.println("  Вы пока ничего не озвучивали.");
                console.println();
                console.println("  0  Назад");
                console.ask("> ");
                return;
            }
            for (int i = 0; i < mine.size(); i++) {
                console.println("  " + (i + 1) + "  " + describe(mine.get(i)));
            }
            console.println();
            List<Voicing> ready = readyToPublish(mine);
            if (!ready.isEmpty()) {
                console.println("  a  Опубликовать все готовые (" + ready.size() + ")");
            }
            console.println("  0  Назад");

            String command = console.ask("> ");
            if (command.equals("0")) {
                return;
            }
            if (command.equalsIgnoreCase("a")) {
                publishAll(mine, ready);
                continue;
            }
            OptionalInt index = Console.index(command, mine.size());
            if (index.isEmpty()) {
                console.println("Не понимаю. Введите номер из списка или 0.");
                continue;
            }
            openRole(mine.get(index.getAsInt()));
        }
    }

    private String describe(Voicing voicing) {
        Optional<Book> book = books.find(voicing.bookId());
        String bookTitle = book.map(Book::title).orElse(voicing.bookId());
        String speakerName = book.flatMap(found -> found.speaker(voicing.speakerId()))
                .map(Speaker::name).orElse(voicing.speakerId());
        String progress = book
                .map(found -> voicings.recordedCount(voicing, found) + "/" + found.linesOf(voicing.speakerId()).size())
                .orElse("?");
        String status = voicing.status() == VoicingStatus.PUBLISHED ? "опубликовано" : "черновик";
        String rating = voting.rated(voicing.id())
                .map(rated -> "  [" + (rated.score() > 0 ? "+" + rated.score() : rated.score()) + "]")
                .orElse("");
        return bookTitle + " · " + speakerName + "  " + status + "  " + progress + rating;
    }

    /** Черновики, у которых записаны все реплики, — их можно опубликовать разом. */
    private List<Voicing> readyToPublish(List<Voicing> mine) {
        return mine.stream()
                .filter(voicing -> voicing.status() == VoicingStatus.DRAFT)
                .filter(voicing -> books.find(voicing.bookId())
                        .map(book -> voicings.isComplete(voicing, book))
                        .orElse(false))
                .toList();
    }

    private void publishAll(List<Voicing> mine, List<Voicing> ready) {
        if (ready.isEmpty()) {
            console.println("Нет черновиков, у которых записаны все реплики.");
            return;
        }
        for (Voicing voicing : ready) {
            voicings.publish(voicing, books.find(voicing.bookId()).orElseThrow());
        }
        console.println("Опубликовано: " + Plural.of(ready.size(), "роль", "роли", "ролей") + ".");
        List<Voicing> unfinished = mine.stream()
                .filter(voicing -> voicing.status() == VoicingStatus.DRAFT && !ready.contains(voicing))
                .toList();
        if (!unfinished.isEmpty()) {
            console.println("Остались черновиками — записаны не все реплики:");
            unfinished.forEach(voicing -> console.println("  " + describe(voicing)));
        }
    }

    private void openRole(Voicing voicing) {
        Optional<Book> book = books.find(voicing.bookId());
        if (book.isEmpty()) {
            console.println("Книга " + voicing.bookId() + " больше не входит в комплект.");
            return;
        }
        while (true) {
            Voicing current = voicings.reload(voicing);
            console.println();
            console.println(describe(current));
            console.println();
            console.println("  1  Продолжить запись");
            console.println("  2  Прослушать роль целиком");
            console.println("  3  " + (current.status() == VoicingStatus.PUBLISHED
                    ? "Снять с публикации" : "Опубликовать"));
            console.println("  4  Удалить");
            console.println("  0  Назад");

            switch (console.ask("> ")) {
                case "1" -> record.recordRole(book.get(), current);
                case "2" -> playRole(book.get(), current);
                case "3" -> togglePublication(book.get(), current);
                case "4" -> {
                    if (deleteConfirmed(current)) {
                        return;
                    }
                }
                case "0" -> {
                    return;
                }
                default -> console.println("Не понимаю. Введите число от 0 до 4.");
            }
        }
    }

    /** Свой персонаж звучит из этой роли даже в черновике, остальные берутся из лучших. */
    private void playRole(Book book, Voicing voicing) {
        Cast cast = castBuilder.best(book).with(voicing.speakerId(), voicing.id());
        playbackConsole.play(playback.plan(book, cast));
    }

    private void togglePublication(Book book, Voicing voicing) {
        if (voicing.status() == VoicingStatus.PUBLISHED) {
            voicings.unpublish(voicing);
            console.println("Роль снята с публикации. Голоса сохранены.");
            return;
        }
        try {
            voicings.publish(voicing, book);
            console.println("Роль опубликована.");
        } catch (IllegalStateException e) {
            console.println(e.getMessage());
        }
    }

    private boolean deleteConfirmed(Voicing voicing) {
        String answer = console.ask("Удалить роль вместе с записями? да / нет: ");
        if (!answer.equalsIgnoreCase("да")) {
            console.println("Оставляем как есть.");
            return false;
        }
        voicings.delete(voicing);
        console.println("Роль удалена.");
        return true;
    }
}
