package space.grayt.teremok.domain;

import static org.junit.jupiter.api.Assertions.*;
import static space.grayt.teremok.domain.VoicingStatus.*;

import org.junit.jupiter.api.Test;

class VoicingStatusTest {

    @Test
    void draftCanOnlyBePublished() {
        assertTrue(DRAFT.canMoveTo(PUBLISHED));
        assertFalse(DRAFT.canMoveTo(ARCHIVED));
        assertFalse(DRAFT.canMoveTo(DRAFT));
    }

    @Test
    void publishedCanOnlyBeArchived() {
        assertTrue(PUBLISHED.canMoveTo(ARCHIVED));
        assertFalse(PUBLISHED.canMoveTo(PUBLISHED));
        assertFalse(PUBLISHED.canMoveTo(DRAFT));
    }

    @Test
    void archivedCanOnlyBePublishedAgain() {
        assertTrue(ARCHIVED.canMoveTo(PUBLISHED));
        assertFalse(ARCHIVED.canMoveTo(ARCHIVED));
        assertFalse(ARCHIVED.canMoveTo(DRAFT));
    }

    @Test
    void nothingGoesBackToDraft() {
        for (VoicingStatus status : values()) {
            assertFalse(status.canMoveTo(DRAFT), status::name);
        }
    }
}
