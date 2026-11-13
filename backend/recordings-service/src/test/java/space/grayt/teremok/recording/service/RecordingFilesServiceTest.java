package space.grayt.teremok.recording.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.client.contract.RenderJobResponse;
import space.grayt.teremok.storage.ObjectStorage;

class RecordingFilesServiceTest {

  @Test
  void checksOwnershipReadinessAndIndexBeforeOpeningStorage() {
    var recordings = mock(RecordingService.class);
    var storage = mock(ObjectStorage.class);
    var service = new RecordingFilesService(recordings, storage);
    UUID owner = UUID.randomUUID(),
      render = UUID.randomUUID();
    when(recordings.findRender(owner, render)).thenThrow(
      new RecordingNotFoundException("not found")
    );
    assertThatThrownBy(() -> service.download(owner, render, 0)).isInstanceOf(
      RecordingNotFoundException.class
    );
    doReturn(job(render, "PENDING", List.of()))
      .when(recordings)
      .findRender(owner, render);
    assertThatThrownBy(() -> service.download(owner, render, 0)).isInstanceOf(
      RecordingNotFoundException.class
    );
    when(recordings.findRender(owner, render)).thenReturn(
      job(
        render,
        "COMPLETED",
        List.of(
          new RenderJobResponse.RenderOutput(null, "result.mp3", "audio/mpeg")
        )
      )
    );
    assertThatThrownBy(() -> service.download(owner, render, -1)).isInstanceOf(
      RecordingNotFoundException.class
    );
    assertThatThrownBy(() -> service.download(owner, render, 1)).isInstanceOf(
      RecordingNotFoundException.class
    );
    verifyNoInteractions(storage);
    var stream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
    when(storage.open("result.mp3")).thenReturn(stream);
    var result = service.download(owner, render, 0);
    assertThat(result.stream()).isSameAs(stream);
    assertThat(result.contentType()).isEqualTo("audio/mpeg");
    verify(storage).open("result.mp3");
  }

  private RenderJobResponse job(
    UUID id,
    String status,
    List<RenderJobResponse.RenderOutput> outputs
  ) {
    return new RenderJobResponse(
      id,
      UUID.randomUUID(),
      "SINGLE_FILE",
      status,
      Instant.now(),
      null,
      null,
      outputs
    );
  }
}
