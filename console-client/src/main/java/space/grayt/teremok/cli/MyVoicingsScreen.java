package space.grayt.teremok.cli;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
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
import space.grayt.teremok.exception.BusinessRuleException;
import space.grayt.teremok.exception.EntityNotFoundException;
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
            List<Voicing> ready = voicings.readyToPublish(mine);
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
        int score = voting.rate(voicing).score();
        String rating = "  [" + (score > 0 ? "+" + score : score) + "]";
        return textWorkTitle + " · " + voicePartName + "  " + statusLabel(voicing.status()) + "  " + progress + rating;
    }

    private static String statusLabel(VoicingStatus status) {
        return switch (status) {
            case DRAFT -> "черновик";
            case PUBLISHED -> "опубликовано";
            case ARCHIVED -> "снято с публикации";
        };
    }

    /** All ready drafts are published in one transaction: either every one of them or none. */
    private void publishAll(List<Voicing> mine, List<Voicing> ready) {
        if (ready.isEmpty()) {
            console.println("Нет черновиков, у которых записаны все фрагменты.");
            return;
        }
        try {
            voicings.publishAll(ready);
        } catch (BusinessRuleException | EntityNotFoundException e) {
            console.println(e.getMessage());
            return;
        }
        console.println("Опубликовано: " + Plural.of(ready.size(), "роль", "роли", "ролей") + ".");
        Set<Long> published = ready.stream().map(Voicing::id).collect(Collectors.toSet());
        List<Voicing> unfinished = mine.stream()
                .filter(voicing -> voicing.status() == VoicingStatus.DRAFT && !published.contains(voicing.id()))
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
            Voicing current;
            try {
                current = voicings.reload(voicing);
            } catch (EntityNotFoundException e) {
                console.println(e.getMessage());
                return;
            }
            console.println();
            console.println(describe(current));
            console.println();
            console.println("  1  Продолжить запись");
            console.println("  2  Прослушать роль целиком");
            console.println("  3  " + publicationAction(current.status()));
            console.println("  4  Удалить");
            console.println("  0  Назад");

            switch (console.ask("> ")) {
                case "1" -> record.recordRole(textWork.get(), current);
                case "2" -> playRole(textWork.get(), current);
                case "3" -> changePublication(textWork.get(), current);
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

    private static String publicationAction(VoicingStatus status) {
        return switch (status) {
            case DRAFT -> "Опубликовать";
            case PUBLISHED -> "Снять с публикации";
            case ARCHIVED -> "Вернуть в публикацию";
        };
    }

    private void changePublication(TextWork textWork, Voicing voicing) {
        try {
            switch (voicing.status()) {
                case DRAFT -> {
                    voicings.publish(voicing, textWork);
                    console.println("Роль опубликована.");
                }
                case PUBLISHED -> {
                    voicings.archive(voicing);
                    console.println("Роль снята с публикации. Голоса сохранены, но слушателям она больше не предлагается.");
                }
                case ARCHIVED -> {
                    voicings.publish(voicing, textWork);
                    console.println("Роль снова опубликована.");
                }
            }
        } catch (BusinessRuleException | EntityNotFoundException e) {
            console.println(e.getMessage());
        }
    }

    /** true means the voicing is gone and its screen should close. */
    private boolean deleteConfirmed(Voicing voicing) {
        if (!voicings.canDelete(voicing)) {
            console.println("Опубликованную роль удалить нельзя. Сначала снимите её с публикации.");
            return false;
        }
        String answer = console.ask("Удалить роль вместе с записями и голосами? да / нет: ");
        if (!answer.equalsIgnoreCase("да")) {
            console.println("Оставляем как есть.");
            return false;
        }
        try {
            voicings.delete(voicing);
        } catch (BusinessRuleException e) {
            console.println(e.getMessage());
            return false;
        } catch (EntityNotFoundException e) {
            console.println(e.getMessage());
            return true;
        }
        console.println("Роль удалена.");
        return true;
    }
}
