package space.grayt.teremok.catalog.library.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "library_work")
public class LibraryWork {

  @Id
  public UUID id;

  @Column(length = 64, nullable = false)
  public String code;

  @Column(length = 240, nullable = false)
  public String title;

  @Column(length = 300, nullable = false)
  public String author;

  @Column(length = 3000, nullable = false)
  public String description = "";

  @Column(length = 32, nullable = false)
  public String language = "rus";

  @Column(name = "origin_type", nullable = false, length = 32)
  public String originType = "ORIGINAL";

  @Column(name = "collection_id", nullable = false)
  public UUID collectionId;

  @Column(length = 20, nullable = false)
  public String status = "DRAFT";

  @Column(name = "target_minutes")
  public int targetMinutes;

  public boolean featured;

  @Column(name = "roles_count")
  public int rolesCount;

  @Column(length = 16, nullable = false)
  public String complexity;

  public boolean archived;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "updated_at")
  public Instant updatedAt;

  @Version
  public long version;

  @Column(columnDefinition = "text", nullable = false)
  public String script = "";

  @Column(name = "source_payload", columnDefinition = "text")
  public String sourcePayload;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
    name = "library_tag",
    joinColumns = @JoinColumn(name = "work_id")
  )
  @Column(name = "tag", length = 60)
  public Set<String> tags = new LinkedHashSet<>();
}
