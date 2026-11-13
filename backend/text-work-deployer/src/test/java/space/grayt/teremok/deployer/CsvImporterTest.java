package space.grayt.teremok.deployer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CsvImporterTest {
    @Test void supportsPreviewAndExplicitApplyWithBothModes() {
        var check = CsvImporter.Options.parse(new String[]{"--csv", "books.csv", "--check"});
        assertEquals(Path.of("books.csv"), check.file());
        assertEquals("NEW", check.mode());
        assertTrue(check.check());
        assertFalse(check.yes());
        var apply = CsvImporter.Options.parse(new String[]{"--csv", "books.csv", "--mode", "upsert", "--yes"});
        assertEquals("UPSERT", apply.mode());
        assertTrue(apply.yes());
    }
    @Test void rejectsAmbiguousOrIncompleteCommandsBeforeAnyNetworkCall() {
        for (String[] args : new String[][]{{"--csv"},{"--csv","a.csv","--mode"},{"--csv","a.csv","--mode","DELETE"},{"--csv","a.csv","--yes","--check"},{"--csv","a.csv","--unknown"}})
            assertThrows(IllegalArgumentException.class, () -> CsvImporter.Options.parse(args));
    }
}
