package space.grayt.teremok.cli;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.audio.RecordingSession;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicePart;
import space.grayt.teremok.textwork.TextWorkCatalog;

/** Voicing a voice part fragment by fragment, saving each recording immediately. */
public final class RecordFlow {

    private final Console console;
    private final TextWorkCatalog textWorks;
    private final VoicingService voicings;
    private final AudioPlayer player;

    public RecordFlow(Console console, TextWorkCatalog textWorks, VoicingService voicings, AudioPlayer player) {
        this.console = console;
        this.textWorks = textWorks;
        this.voicings = voicings;
        this.player = player;
    }

    public void run(Profile profile) {
        Optional<TextWork> textWork = chooseTextWork();
        if (textWork.isEmpty()) {
            return;
        }
        while (true) {
            Optional<VoicePart> voicePart = chooseVoicePart(textWork.get(), profile);
            if (voicePart.isEmpty()) {
                return;
            }
            recordRole(
                    textWork.get(),
                    voicings.draftFor(textWork.get(), voicePart.get().id(), profile.id()));
        }
    }

    /** Records a specific voicing. Returns when the user leaves. */
    public void recordRole(TextWork textWork, Voicing voicing) {
        Voicing current = voicings.reload(voicing);
        while (true) {
            List<TextWorkFragment> missing = voicings.missingFragments(current, textWork);
            TextWorkFragment target;
            if (missing.isEmpty()) {
                console.println("Все фрагменты записаны. Роль можно опубликовать в «Моих озвучках».");
                Optional<TextWorkFragment> chosen = chooseFragment(textWork, current);
                if (chosen.isEmpty()) {
                    return;
                }
                target = chosen.get();
            } else {
                target = missing.get(0);
            }
            Optional<Voicing> next = recordFragment(textWork, current, target);
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

    /** An empty result means the user left recording. */
    private Optional<Voicing> recordFragment(
            TextWork textWork,
            Voicing voicing,
            TextWorkFragment startFragment) {
        TextWorkFragment fragment = startFragment;
        while (true) {
            List<TextWorkFragment> all = textWork.fragmentsOf(voicing.voicePartId());
            String voicePartName = textWork.voicePart(voicing.voicePartId())
                    .map(VoicePart::name)
                    .orElse(voicing.voicePartId());

            console.println();
            console.println(voicePartName + " — фрагмент "
                    + (all.indexOf(fragment) + 1) + " из " + all.size());
            console.println();
            console.println("  " + fragment.text());
            console.println();

            String command = console.ask("Enter — начать запись, s — список фрагментов, 0 — выйти: ");
            if (command.equals("0")) {
                return Optional.empty();
            }
            if (command.equals("s")) {
                Optional<TextWorkFragment> chosen = chooseFragment(textWork, voicings.reload(voicing));
                if (chosen.isEmpty()) {
                    return Optional.empty();
                }
                fragment = chosen.get();
                continue;
            }
            if (!record(voicing, fragment)) {
                return Optional.empty();
            }
            if (afterRecording(voicing, fragment) == AfterRecording.EXIT) {
                return Optional.empty();
            }
            return Optional.of(voicings.reload(voicing));
        }
    }

    private AfterRecording afterRecording(Voicing voicing, TextWorkFragment fragment) {
        while (true) {
            switch (console.ask("1 прослушать, 2 перезаписать, 3 дальше, 0 выйти: ")) {
                case "1" -> playRecorded(voicing, fragment);
                case "2" -> {
                    if (!record(voicing, fragment)) {
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

    /** All fragments of the voice part with marks; a recorded fragment can be re-recorded here. */
    private Optional<TextWorkFragment> chooseFragment(TextWork textWork, Voicing voicing) {
        List<TextWorkFragment> all = textWork.fragmentsOf(voicing.voicePartId());
        console.println();
        console.println("Фрагменты роли:");
        console.println();
        for (int i = 0; i < all.size(); i++) {
            TextWorkFragment fragment = all.get(i);
            String mark = voicing.isRecorded(fragment.number()) ? "готово" : "пусто ";
            console.println("  " + (i + 1) + "  [" + mark + "] " + fragment.text());
        }
        console.println("  0  Назад");
        return pick(all);
    }

    /** false means recording failed and the voicing screen should close. */
    private boolean record(Voicing voicing, TextWorkFragment fragment) {
        try {
            RecordingSession session = voicings.startRecording(voicing, fragment.number());
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

    private void playRecorded(Voicing voicing, TextWorkFragment fragment) {
        try {
            player.play(voicings.audioFile(voicing, fragment.number()));
        } catch (AudioUnavailableException e) {
            console.println(e.getMessage());
        }
    }

    private Optional<TextWork> chooseTextWork() {
        List<TextWork> all = textWorks.all();
        if (all.isEmpty()) {
            console.println("Нет ни одного произведения.");
            return Optional.empty();
        }
        console.println();
        console.println("Какое произведение озвучиваем?");
        console.println();
        for (int i = 0; i < all.size(); i++) {
            TextWork textWork = all.get(i);
            console.println("  " + (i + 1) + "  " + textWork.title() + " — "
                    + Plural.fragments(textWork.fragments().size()));
        }
        console.println("  0  Назад");
        return pick(all);
    }

    private Optional<VoicePart> chooseVoicePart(TextWork textWork, Profile profile) {
        List<VoicePart> voiceParts = textWork.voiceParts();
        console.println();
        console.println(textWork.title() + " — какую роль озвучиваем?");
        console.println();
        for (int i = 0; i < voiceParts.size(); i++) {
            VoicePart voicePart = voiceParts.get(i);
            int recorded = voicings.recordedCountFor(textWork, voicePart.id(), profile.id());
            int total = textWork.fragmentsOf(voicePart.id()).size();
            String progress = recorded == 0 ? "не начато" : recorded + "/" + total;
            console.println("  " + (i + 1) + "  " + voicePart.name() + " — "
                    + Plural.fragments(total) + ", " + progress);
        }
        console.println("  0  Назад");
        return pick(voiceParts);
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
