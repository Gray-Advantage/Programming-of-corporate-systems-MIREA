package space.grayt.teremok;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.ProfileService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.db.TestDatabase;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.storage.AudioStorage;
import space.grayt.teremok.storage.JdbcProfileRepository;
import space.grayt.teremok.storage.JdbcVoicingRepository;
import space.grayt.teremok.storage.ProfileRepository;
import space.grayt.teremok.storage.VoicingRepository;
import space.grayt.teremok.textwork.JdbcTextWorkCatalog;
import space.grayt.teremok.textwork.TextWorkCatalog;

/**
 * An H2 database, an audio directory and fake audio devices wired the way App wires them, plus
 * shortcuts that put voicings into a given state without going through the screens.
 */
public final class Fixture {

    public static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

    public final DatabaseManager database;
    public final TextWorkCatalog textWorks;
    public final ProfileRepository profileRepository;
    public final VoicingRepository voicingRepository;
    public final AudioStorage audio;
    public final FakeAudioRecorder recorder = new FakeAudioRecorder();
    public final FakeAudioPlayer player = new FakeAudioPlayer();
    public final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private Fixture(DatabaseManager database, Path audioDir) {
        this.database = database;
        this.textWorks = new JdbcTextWorkCatalog(database);
        this.profileRepository = new JdbcProfileRepository(database);
        this.voicingRepository = new JdbcVoicingRepository(database);
        this.audio = new AudioStorage(audioDir);
    }

    /** The three fairy tales of seed.sql, no profiles or voicings. */
    public static Fixture withSeedTextWorks(Path audioDir) {
        return new Fixture(TestDatabase.withTextWorks(), audioDir);
    }

    /** No text works: a test adds its own small ones with {@link #addTextWork}. */
    public static Fixture empty(Path audioDir) {
        return new Fixture(TestDatabase.empty(), audioDir);
    }

    /** Must be called before the catalog is first used: it reads the text works once. */
    public TextWork addTextWork(String title, String... lines) {
        return TestDatabase.addTextWork(database, title, lines);
    }

    public ProfileService profiles() {
        return new ProfileService(profileRepository);
    }

    public VotingService voting() {
        return new VotingService(voicingRepository);
    }

    public CastBuilder castBuilder() {
        return new CastBuilder(voting());
    }

    public VoicingService voicings() {
        return new VoicingService(voicingRepository, textWorks, audio, recorder, clock);
    }

    public PlaybackService playback() {
        return new PlaybackService(voicingRepository, audio);
    }

    public TextWork textWork(String title) {
        return TestDatabase.textWork(textWorks, title);
    }

    public Profile profile(String name) {
        Profile wanted = Profile.of(name);
        return profileRepository.findById(wanted.id()).orElseGet(() -> profileRepository.create(name));
    }

    public Voicing draft(TextWork textWork, String voicePartName, String author) {
        return draft(textWork, voicePartName, author, NOW);
    }

    public Voicing draft(TextWork textWork, String voicePartName, String author, Instant createdAt) {
        profile(author);
        return voicingRepository.insert(TestDatabase.voicePartId(textWork, voicePartName), author, createdAt);
    }

    /** Writes a placeholder file for each fragment and stores the recording rows. */
    public Voicing record(Voicing voicing, int... fragmentNumbers) {
        for (int number : fragmentNumbers) {
            Path file = audio.fileFor(voicing.id(), number);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "звук");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            voicingRepository.markRecorded(voicing.id(), number, AudioStorage.relativePath(voicing.id(), number), 1000);
        }
        return reload(voicing);
    }

    /** Records the first fragments of the voice part. */
    public Voicing recordFirst(Voicing voicing, TextWork textWork, int count) {
        List<TextWorkFragment> fragments = textWork.fragmentsOf(voicing.voicePartId());
        return record(voicing, fragments.subList(0, count).stream().mapToInt(TextWorkFragment::number).toArray());
    }

    public Voicing recordAll(Voicing voicing, TextWork textWork) {
        return recordFirst(voicing, textWork, textWork.fragmentsOf(voicing.voicePartId()).size());
    }

    /** Sets the status directly, past the service rules; an archived voicing is published first. */
    public Voicing withStatus(Voicing voicing, VoicingStatus status) {
        if (status == VoicingStatus.ARCHIVED) {
            voicingRepository.updateStatus(voicing.id(), VoicingStatus.PUBLISHED, NOW);
        }
        voicingRepository.updateStatus(voicing.id(), status, NOW);
        return reload(voicing);
    }

    /** A fully recorded voicing in the given status. */
    public Voicing voicing(TextWork textWork, String voicePartName, String author, VoicingStatus status) {
        Voicing voicing = recordAll(draft(textWork, voicePartName, author), textWork);
        return status == VoicingStatus.DRAFT ? voicing : withStatus(voicing, status);
    }

    public void vote(Voicing voicing, VoteKind kind, String... voters) {
        for (String voter : voters) {
            profile(voter);
            voicingRepository.putVote(voicing.id(), voter, kind);
        }
    }

    /** Likes from voters fan0, fan1, ... */
    public void likes(Voicing voicing, int count) {
        for (int i = 0; i < count; i++) {
            vote(voicing, VoteKind.LIKE, "fan" + i);
        }
    }

    public Voicing reload(Voicing voicing) {
        return voicingRepository.find(voicing.id()).orElseThrow();
    }

    public String voicePartId(TextWork textWork, String voicePartName) {
        return TestDatabase.voicePartId(textWork, voicePartName);
    }
}
