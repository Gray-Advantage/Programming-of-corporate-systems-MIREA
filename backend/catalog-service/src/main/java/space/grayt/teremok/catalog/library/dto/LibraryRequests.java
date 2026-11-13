package space.grayt.teremok.catalog.library.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class LibraryRequests {

  private LibraryRequests() {}

  public record Bulk(
    @NotEmpty @Size(max = 100) List<@NotNull UUID> ids,
    @NotBlank String operation,
    String value
  ) {}

  public record CollectionInput(
    @NotBlank @Size(max = 120) String name,
    boolean active
  ) {}

  public record NoteInput(
    @NotBlank @Size(max = 3000) String content,
    boolean pinned
  ) {}
}
