package space.grayt.teremok.catalog.library.controller;

import static space.grayt.teremok.catalog.library.security.RequestIdentity.actor;
import static space.grayt.teremok.http.ApiErrors.correlation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.grayt.teremok.catalog.library.dto.*;
import space.grayt.teremok.catalog.library.dto.LibraryRequests.*;
import space.grayt.teremok.catalog.library.filter.WorkFilter;
import space.grayt.teremok.catalog.library.service.LibraryService;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryController {

  private final LibraryService library;
  private final space.grayt.teremok.catalog.library.service.LibraryManagementService management;

  public LibraryController(
    LibraryService library,
    space.grayt.teremok.catalog.library.service.LibraryManagementService management
  ) {
    this.library = library;
    this.management = management;
  }

  @GetMapping("/works")
  public Object list(
    @ModelAttribute WorkFilter filter,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "12") int size,
    @RequestParam(defaultValue = "title") String sort,
    @RequestParam(defaultValue = "asc") String direction
  ) {
    return management.list(filter, page, size, sort, direction);
  }

  @GetMapping("/works/{id}")
  public WorkView detail(@PathVariable UUID id) {
    return management.detail(id);
  }

  @PostMapping("/works")
  @ResponseStatus(HttpStatus.CREATED)
  public WorkView create(
    @Valid @RequestBody WorkInput input,
    Authentication auth,
    HttpServletRequest request
  ) {
    return library.save(null, input, actor(auth), correlation(request));
  }

  @PutMapping("/works/{id}")
  public WorkView update(
    @PathVariable UUID id,
    @Valid @RequestBody WorkInput input,
    Authentication auth,
    HttpServletRequest request
  ) {
    return library.save(id, input, actor(auth), correlation(request));
  }

  @DeleteMapping("/works/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(
    @PathVariable UUID id,
    @RequestParam long version,
    Authentication auth,
    HttpServletRequest request
  ) {
    library.operation(
      id,
      "archive",
      null,
      version,
      actor(auth),
      correlation(request)
    );
  }

  @PostMapping("/works/{id}/restore")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void restore(
    @PathVariable UUID id,
    @RequestParam long version,
    Authentication auth,
    HttpServletRequest request
  ) {
    library.operation(
      id,
      "restore",
      null,
      version,
      actor(auth),
      correlation(request)
    );
  }

  @DeleteMapping("/works/{id}/permanent")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void permanent(
    @PathVariable UUID id,
    @RequestParam long version,
    Authentication auth,
    HttpServletRequest request
  ) {
    library.operation(
      id,
      "permanent",
      null,
      version,
      actor(auth),
      correlation(request)
    );
  }

  @PostMapping("/works/bulk")
  public Object bulk(
    @Valid @RequestBody Bulk input,
    Authentication auth,
    HttpServletRequest request
  ) {
    return management.bulk(input, actor(auth), correlation(request));
  }

  @GetMapping("/collections")
  public Object collections() {
    return management.collections();
  }

  @PostMapping("/collections")
  @ResponseStatus(HttpStatus.CREATED)
  public Object createCollection(
    @Valid @RequestBody CollectionInput input,
    Authentication auth,
    HttpServletRequest request
  ) {
    return management.createCollection(
      input,
      actor(auth),
      correlation(request)
    );
  }

  @PutMapping("/collections/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateCollection(
    @PathVariable UUID id,
    @Valid @RequestBody CollectionInput input,
    Authentication auth,
    HttpServletRequest request
  ) {
    management.updateCollection(id, input, actor(auth), correlation(request));
  }

  @GetMapping("/works/{id}/notes")
  public Object notes(@PathVariable UUID id) {
    return management.notes(id);
  }

  @PostMapping("/works/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addNote(
    @PathVariable UUID id,
    @Valid @RequestBody NoteInput input,
    Authentication auth,
    HttpServletRequest request
  ) {
    return management.addNote(id, input, actor(auth), correlation(request));
  }

  @PutMapping("/works/{id}/notes/{noteId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void editNote(
    @PathVariable UUID id,
    @PathVariable UUID noteId,
    @Valid @RequestBody NoteInput input,
    Authentication auth,
    HttpServletRequest request
  ) {
    management.editNote(id, noteId, input, actor(auth), correlation(request));
  }

  @DeleteMapping("/works/{id}/notes/{noteId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteNote(
    @PathVariable UUID id,
    @PathVariable UUID noteId,
    Authentication auth,
    HttpServletRequest request
  ) {
    management.deleteNote(id, noteId, actor(auth), correlation(request));
  }

  @GetMapping("/analytics/summary")
  public Object analytics() {
    return management.analytics();
  }

  @GetMapping("/audit")
  public Object audit(
    Authentication auth,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "desc") String direction
  ) {
    return management.audit(actor(auth), q, page, size, direction);
  }
}
