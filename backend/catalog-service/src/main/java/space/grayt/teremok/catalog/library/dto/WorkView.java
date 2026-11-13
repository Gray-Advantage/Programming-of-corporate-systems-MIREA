package space.grayt.teremok.catalog.library.dto;

import java.time.Instant;
import java.util.*;
import space.grayt.teremok.catalog.library.domain.LibraryWork;

public record WorkView(
  UUID id,
  String code,
  String title,
  String author,
  String description,
  String language,
  UUID collectionId,
  String status,
  int targetMinutes,
  boolean featured,
  int rolesCount,
  String complexity,
  boolean archived,
  Instant createdAt,
  Instant updatedAt,
  long version,
  Set<String> tags,
  String script,
  String originType
) {
  public static WorkView from(LibraryWork w) {
    return new WorkView(
      w.id,
      w.code,
      w.title,
      w.author,
      w.description,
      w.language,
      w.collectionId,
      w.status,
      w.targetMinutes,
      w.featured,
      w.rolesCount,
      w.complexity,
      w.archived,
      w.createdAt,
      w.updatedAt,
      w.version,
      Set.copyOf(w.tags),
      w.script,
      w.originType
    );
  }
}
