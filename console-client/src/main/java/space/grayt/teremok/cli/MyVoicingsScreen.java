package space.grayt.teremok.cli;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.VoicePart;
import space.grayt.teremok.textwork.TextWorkCatalog;

/** The user's own voicings and actions on them. */
public final class MyVoicingsScreen {

    private final Console console;
    private final TextWorkCatalog textWorks;
    private final VoicingService voicings;
    private final VotingService voting;
    private final CastBuilder castBuilder;
    private final PlaybackService playback;
    private final PlaybackConsole playbackConsole;
    private final RecordFlow record;

    public MyVoicingsScreen(Console console, TextWorkCatalog textWorks, VoicingService voicings,
                            VotingService voting,
                            CastBuilder castBuilder, PlaybackService playback, PlaybackConsole playbackConsole,
                            RecordFlow record) {
        this.console = console;
        this.textWorks = textWorks;
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
        Optional<TextWork> textWork = textWorks.find(voicing.textWorkId());
        String textWorkTitle = textWork.map(TextWork::title).orElse(voicing.textWorkId());
        String voicePartName = textWork.flatMap(found -> found.voicePart(voicing.voicePartId()))
                .map(VoicePart::name).orElse(voicing.voicePartId());
        String progress = textWork
                .map(found -> voicings.recordedCount(voicing, found) + "/"
                        + found.fragmentsOf(voicing.voicePartId()).size())
                .orElse("?");
        String status = voicing.status() == VoicingStatus.PUBLISHED ? "опубликовано" : "черновик";
        String rating = voting.rated(voicing.id())
                .map(rated -> "  [" + (rated.score() > 0 ? "+" + rated.score() : rated.score()) + "]")
                .orElse("");
        return textWorkTitle + " · " + voicePartName + "  " + status + "  " + progress + rating;
    }

    /** Drafts with every fragment recorded; these can be published all at once. */
    private List<Voicing> readyToPublish(List<Voicing> mine) {
        return mine.stream()
                .filter(voicing -> voicing.status() == VoicingStatus.DRAFT)
                .filter(voicing -> textWorks.find(voicing.textWorkId())
                        .map(textWork -> voicings.isComplete(voicing, textWork))
                        .orElse(false))
                .toList();
    }

    private void publishAll(List<Voicing> mine, List<Voicing> ready) {
        if (ready.isEmpty()) {
            console.println("Нет черновиков, у которых записаны все фрагменты.");
            return;
        }
        for (Voicing voicing : ready) {
            voicings.publish(voicing, textWorks.find(voicing.textWorkId()).orElseThrow());
        }
        console.println("Опубликовано: " + Plural.of(ready.size(), "роль", "роли", "ролей") + ".");
        List<Voicing> unfinished = mine.stream()
                .filter(voicing -> voicing.status() == VoicingStatus.DRAFT && !ready.contains(voicing))
                .toList();
        if (!unfinished.isEmpty()) {
            console.println("Остались черновиками — записаны не все фрагменты:");
            unfinished.forEach(voicing -> console.println("  " + describe(voicing)));
        }
    }

    private void openRole(Voicing voicing) {
        Optional<TextWork> textWork = textWorks.find(voicing.textWorkId());
        if (textWork.isEmpty()) {
            console.println("Произведение " + voicing.textWorkId() + " больше не доступно.");
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
                case "1" -> record.recordRole(textWork.get(), current);
                case "2" -> playRole(textWork.get(), current);
                case "3" -> togglePublication(textWork.get(), current);
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

    /** The user's own voice part plays from this voicing even while it is a draft. */
    private void playRole(TextWork textWork, Voicing voicing) {
        Cast cast = castBuilder.best(textWork).with(voicing.voicePartId(), voicing.id());
        playbackConsole.play(playback.plan(textWork, cast));
    }

    private void togglePublication(TextWork textWork, Voicing voicing) {
        if (voicing.status() == VoicingStatus.PUBLISHED) {
            voicings.unpublish(voicing);
            console.println("Роль снята с публикации. Голоса сохранены.");
            return;
        }
        try {
            voicings.publish(voicing, textWork);
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
