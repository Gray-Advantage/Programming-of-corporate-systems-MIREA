package space.grayt.teremok.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioStorageTest {

    @Test
    void relativePathUsesVoicingFolderAndFourDigits() {
        assertEquals("7/fragment-0003.wav", AudioStorage.relativePath(7, 3));
        assertEquals("12/fragment-0100.wav", AudioStorage.relativePath(12, 100));
    }

    @Test
    void fileIsResolvedInsideRoot(@TempDir Path dir) {
        AudioStorage audio = new AudioStorage(dir.resolve("audio"));

        assertEquals(dir.resolve("audio/1/fragment-0001.wav"), audio.fileFor(1, 1));
        assertEquals(audio.fileFor(1, 1), audio.resolve("1/fragment-0001.wav").orElseThrow());
    }

    @Test
    void pathsLeadingOutsideRootAreRefused(@TempDir Path dir) {
        AudioStorage audio = new AudioStorage(dir.resolve("audio"));

        assertTrue(audio.resolve("../secret.wav").isEmpty());
        assertTrue(audio.resolve("1/../../secret.wav").isEmpty());
        assertTrue(audio.resolve(dir.resolve("secret.wav").toString()).isEmpty());
    }

    @Test
    void prepareCreatesFolderAndRemovesStaleTempFiles(@TempDir Path dir) throws Exception {
        AudioStorage audio = new AudioStorage(dir);
        Path folder = Files.createDirectories(dir.resolve("5"));
        Files.writeString(folder.resolve("fragment-0001.wav.tmp"), "обрывок");
        Files.writeString(folder.resolve("fragment-0002.wav"), "звук");

        audio.prepare(5);
        audio.prepare(6);

        assertFalse(Files.exists(folder.resolve("fragment-0001.wav.tmp")));
        assertTrue(Files.exists(folder.resolve("fragment-0002.wav")));
        assertTrue(Files.isDirectory(dir.resolve("6")));
    }

    @Test
    void deletingVoicingRemovesItsFolderOnly(@TempDir Path dir) throws Exception {
        AudioStorage audio = new AudioStorage(dir);
        Files.createDirectories(dir.resolve("5"));
        Files.writeString(dir.resolve("5/fragment-0001.wav"), "звук");
        Files.createDirectories(dir.resolve("50"));

        audio.deleteVoicing(5);
        audio.deleteVoicing(6);

        assertFalse(Files.exists(dir.resolve("5")));
        assertTrue(Files.exists(dir.resolve("50")));
    }

    @Test
    void emptyOrMissingFileIsNotPlayable(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("empty.wav"), "");
        Files.writeString(dir.resolve("sound.wav"), "звук");

        assertFalse(AudioStorage.isPlayable(dir.resolve("empty.wav")));
        assertFalse(AudioStorage.isPlayable(dir.resolve("missing.wav")));
        assertTrue(AudioStorage.isPlayable(dir.resolve("sound.wav")));
    }
}
