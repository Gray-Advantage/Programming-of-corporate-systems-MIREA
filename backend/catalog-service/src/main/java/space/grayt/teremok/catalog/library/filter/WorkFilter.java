package space.grayt.teremok.catalog.library.filter;

import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import space.grayt.teremok.catalog.library.domain.LibraryWork;

public record WorkFilter(
  String q,
  UUID collectionId,
  String status,
  Boolean featured,
  String complexity,
  String tag,
  String archive,
  Integer minutes,
  String comparison,
  String language,
  String originType
) {
  public Specification<LibraryWork> specification() {
    return (root, query, cb) -> {
      var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
      if (q != null && !q.isBlank()) {
        String like =
          "%" +
          q
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_") +
          "%";
        p.add(
          cb.or(
            cb.like(cb.lower(root.get("title")), like, '\\'),
            cb.like(cb.lower(root.get("author")), like, '\\'),
            cb.like(cb.lower(root.get("code")), like, '\\')
          )
        );
      }
      if (collectionId != null) p.add(
        cb.equal(root.get("collectionId"), collectionId)
      );
      if (status != null && !status.isBlank()) p.add(
        cb.equal(root.get("status"), status)
      );
      if (featured != null) p.add(cb.equal(root.get("featured"), featured));
      if (language != null && !language.isBlank()) p.add(
        cb.equal(root.get("language"), language)
      );
      if (originType != null && !originType.isBlank()) p.add(
        cb.equal(root.get("originType"), originType)
      );
      if (complexity != null && !complexity.isBlank()) p.add(
        cb.equal(root.get("complexity"), complexity)
      );
      if (tag != null && !tag.isBlank()) p.add(
        cb.isMember(tag, root.get("tags"))
      );
      if (!"all".equals(archive)) p.add(
        cb.equal(root.get("archived"), "archived".equals(archive))
      );
      if (minutes != null) p.add(
        switch (Objects.toString(comparison, "gte")) {
          case "lt" -> cb.lt(root.get("targetMinutes"), minutes);
          case "lte" -> cb.le(root.get("targetMinutes"), minutes);
          case "gt" -> cb.gt(root.get("targetMinutes"), minutes);
          case "eq" -> cb.equal(root.get("targetMinutes"), minutes);
          default -> cb.ge(root.get("targetMinutes"), minutes);
        }
      );
      return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }
}
