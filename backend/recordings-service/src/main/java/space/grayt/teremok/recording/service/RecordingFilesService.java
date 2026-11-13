package space.grayt.teremok.recording.service;

import java.io.InputStream;
import java.util.UUID;
import org.springframework.stereotype.Service;
import space.grayt.teremok.storage.ObjectStorage;

@Service
public class RecordingFilesService {

  private final RecordingService recordings;
  private final ObjectStorage storage;

  public RecordingFilesService(
    RecordingService recordings,
    ObjectStorage storage
  ) {
    this.recordings = recordings;
    this.storage = storage;
  }

  public AudioDownload download(UUID userId, UUID renderId, int index) {
    var job = recordings.findRender(userId, renderId);
    if (
      !"COMPLETED".equals(job.status()) ||
      index < 0 ||
      index >= job.outputs().size()
    ) throw new RecordingNotFoundException(
      "Аудиосборка ещё не готова или файл не найден"
    );
    var output = job.outputs().get(index);
    return new AudioDownload(
      output.contentType(),
      storage.open(output.objectKey())
    );
  }

  public record AudioDownload(String contentType, InputStream stream) {}
}
