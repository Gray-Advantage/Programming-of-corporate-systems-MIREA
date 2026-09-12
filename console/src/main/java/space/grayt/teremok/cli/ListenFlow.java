package space.grayt.teremok.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoteResult;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.Speaker;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.storage.ProfileRepository;

/** Выбор книги, каста и прослушивание. Голоса ставятся здесь же, в списке вариантов. */
public final class ListenFlow {

    private final Console console;
    private final BookLibrary books;
    private final CastBuilder castBuilder;
    private final VotingService voting;
    private final PlaybackService playback;
    private final PlaybackConsole playbackConsole;
    private final AudioPlayer player;
    private final ProfileRepository profiles;

    public ListenFlow(Console console, BookLibrary books, CastBuilder castBuilder, VotingService voting,
            PlaybackService playback, PlaybackConsole playbackConsole, AudioPlayer player,
            ProfileRepository profiles) {
        this.console = console;
        this.books = books;
        this.castBuilder = castBuilder;
        this.voting = voting;
        this.playback = playback;
        this.playbackConsole = playbackConsole;
        this.player = player;
        this.profiles = profiles;
    }

    public void run(Profile profile) {
        Optional<Book> chosen = chooseBook();
        if (chosen.isEmpty()) {
            return;
        }
        Book book = chosen.get();
        Cast cast = castBuilder.best(book);
        while (true) {
            showCast(book, cast);
            String command = console.ask("> ");
            switch (command) {
                case "s" -> playbackConsole.play(playback.plan(book, cast));
                case "n" -> cast = changeVoice(profile, book, cast);
                case "0" -> {
                    return;
                }
                default -> console.println("Не понимаю. Введите s, n или 0.");
            }
        }
    }

    private void showCast(Book book, Cast cast) {
        console.println();
        console.println(book.title() + " — " + Plural.lines(book.lines().size()));
        console.println();
        List<Speaker> speakers = book.speakers();
        for (int i = 0; i < speakers.size(); i++) {
            Speaker speaker = speakers.get(i);
            String voice = cast.voicingFor(speaker.id())
                    .flatMap(voting::rated)
                    .map(rated -> profiles.nameOf(rated.voicing().authorId()) + "  [" + withSign(rated.score()) + "]")
                    .orElse("— текстом —");
            console.println("  " + (i + 1) + "  " + speaker.name() + "  " + voice);
        }
        console.println();
        console.println("  s  Слушать    n  Сменить голос    0  Назад");
    }

    private Cast changeVoice(Profile profile, Book book, Cast cast) {
        List<Speaker> speakers = book.speakers();
        console.println("Кому меняем голос?");
        Optional<Speaker> speaker = pick(speakers);
        if (speaker.isEmpty()) {
            return cast;
        }
        return chooseVoicing(profile, book, cast, speaker.get());
    }

    private Cast chooseVoicing(Profile profile, Book book, Cast cast, Speaker speaker) {
        while (true) {
            List<RatedVoicing> ranked = voting.ranked(book.id(), speaker.id());
            console.println();
            console.println(speaker.name() + " — " + Plural.lines(book.linesOf(speaker.id()).size()));
            console.println();
            for (int i = 0; i < ranked.size(); i++) {
                RatedVoicing rated = ranked.get(i);
                String mine = voting.voteOf(rated.voicing().id(), profile.id())
                        .map(kind -> kind == VoteKind.LIKE ? "   ваш голос: +" : "   ваш голос: -")
                        .orElse("");
                console.println("  " + (i + 1) + "  " + profiles.nameOf(rated.voicing().authorId())
                        + "  [" + withSign(rated.score()) + "]  "
                        + Plural.of(rated.likes(), "лайк", "лайка", "лайков") + ", "
                        + Plural.of(rated.dislikes(), "дизлайк", "дизлайка", "дизлайков") + mine);
            }
            if (ranked.isEmpty()) {
                console.println("  Опубликованных озвучек пока нет.");
            }
            console.println("  t  Читать текстом");
            console.println("  0  Назад");
            console.println("  Команды: p N — пример, l N — лайк, d N — дизлайк");

            String command = console.ask("> ");
            if (command.equals("0")) {
                return cast;
            }
            if (command.equals("t")) {
                return cast.without(speaker.id());
            }
            Optional<Cast> updated = applyCommand(profile, cast, speaker, ranked, command);
            if (updated.isPresent()) {
                return updated.get();
            }
        }
    }

    /** Непустой результат означает, что каст выбран и экран пора закрыть. */
    private Optional<Cast> applyCommand(Profile profile, Cast cast, Speaker speaker,
            List<RatedVoicing> ranked, String command) {
        if (command.length() > 2 && command.charAt(1) == ' ') {
            OptionalInt index = Console.index(command.substring(2), ranked.size());
            if (index.isEmpty()) {
                console.println("Нет такого номера в списке.");
                return Optional.empty();
            }
            RatedVoicing target = ranked.get(index.getAsInt());
            switch (command.charAt(0)) {
                case 'p' -> playSample(target);
                case 'l' -> vote(profile, target, VoteKind.LIKE);
                case 'd' -> vote(profile, target, VoteKind.DISLIKE);
                default -> console.println("Не понимаю команду.");
            }
            return Optional.empty();
        }
        OptionalInt index = Console.index(command, ranked.size());
        if (index.isPresent()) {
            return Optional.of(cast.with(speaker.id(), ranked.get(index.getAsInt()).voicing().id()));
        }
        console.println("Не понимаю. Введите номер, t, 0 или команду p/l/d с номером.");
        return Optional.empty();
    }

    private void playSample(RatedVoicing target) {
        Optional<Path> sample = playback.sample(target.voicing());
        if (sample.isEmpty()) {
            console.println("У этой озвучки не осталось записанных реплик.");
            return;
        }
        try {
            player.play(sample.get());
        } catch (AudioUnavailableException e) {
            console.println(e.getMessage());
        }
    }

    private void vote(Profile profile, RatedVoicing target, VoteKind kind) {
        VoteResult result = voting.vote(target.voicing().id(), profile.id(), kind);
        String label = kind == VoteKind.LIKE ? "Лайк" : "Дизлайк";
        switch (result) {
            case ADDED -> console.println(label + " поставлен.");
            case CHANGED -> console.println(label + " заменил прежний голос.");
            case REMOVED -> console.println(label + " снят.");
            case REJECTED_OWN -> console.println("За свою озвучку голосовать нельзя.");
            case REJECTED_DRAFT -> console.println("За черновик голосовать нельзя.");
        }
    }

    private Optional<Book> chooseBook() {
        List<Book> all = books.all();
        if (all.isEmpty()) {
            console.println("Нет ни одной книги.");
            return Optional.empty();
        }
        console.println();
        console.println("Что слушаем?");
        console.println();
        for (int i = 0; i < all.size(); i++) {
            console.println("  " + (i + 1) + "  " + all.get(i).title());
        }
        console.println("  0  Назад");
        return pick(all);
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

    private static String withSign(int score) {
        return score > 0 ? "+" + score : String.valueOf(score);
    }
}
