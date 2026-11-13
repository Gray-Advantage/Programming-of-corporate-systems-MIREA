package space.grayt.teremok.catalog.library.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record WorkInput(
  @NotBlank @Size(max = 64) String code,
  @NotBlank @Size(max = 240) String title,
  @NotBlank @Size(max = 300) String author,
  @NotNull @Size(max = 3000) String description,
  @NotBlank @Size(max = 32) String language,
  @NotNull UUID collectionId,
  @NotBlank @Pattern(regexp = "DRAFT|RECORDING|READY") String status,
  @Min(1) @Max(1440) int targetMinutes,
  boolean featured,
  @NotNull @Size(max = 20) Set<@NotBlank @Size(max = 60) String> tags,
  @Size(max = 200000) String script,
  @Min(0) long version
) {}
