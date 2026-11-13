package space.grayt.teremok.auth.web;

import static space.grayt.teremok.http.ApiErrors.correlation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.grayt.teremok.auth.service.IdentityService;

@RestController
public class SessionController {

  private final IdentityService identity;

  public SessionController(IdentityService identity) {
    this.identity = identity;
  }

  @PostMapping("/api/v1/session/login")
  public IdentityService.Session login(
    @Valid @RequestBody Credentials body,
    HttpServletRequest request
  ) {
    return identity.login(
      body.login(),
      body.password(),
      request.getRemoteAddr(),
      correlation(request)
    );
  }

  @PostMapping("/api/v1/session/register")
  @ResponseStatus(HttpStatus.CREATED)
  public IdentityService.Session register(
    @Valid @RequestBody Registration body,
    HttpServletRequest request
  ) {
    return identity.registerSession(
      body.login(),
      body.name(),
      body.password(),
      request.getRemoteAddr(),
      correlation(request)
    );
  }

  @PostMapping("/api/v1/session/refresh")
  public IdentityService.Session refresh(@RequestBody Refresh body) {
    return identity.refresh(body.refreshToken());
  }

  @GetMapping({ "/api/v1/session/me", "/api/v1/auth/me" })
  public IdentityService.Profile me(Authentication auth) {
    return identity.me(id(auth));
  }

  @PostMapping("/api/v1/session/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
    @RequestBody Refresh body,
    Authentication auth,
    HttpServletRequest request
  ) {
    identity.logout(body.refreshToken(), id(auth), correlation(request));
  }

  @PostMapping("/api/v1/session/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(
    @Valid @RequestBody Password body,
    Authentication auth,
    HttpServletRequest request
  ) {
    identity.changePassword(
      id(auth),
      body.currentPassword(),
      body.newPassword(),
      correlation(request)
    );
  }

  @GetMapping("/api/v1/admin/users")
  public Object users(
    Authentication auth,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    return identity.listUsers(id(auth), q, page, size);
  }

  @PostMapping("/api/v1/admin/users")
  @ResponseStatus(HttpStatus.CREATED)
  public Object create(
    @Valid @RequestBody UserInput body,
    Authentication auth,
    HttpServletRequest request
  ) {
    return save(null, body, auth, request);
  }

  @PutMapping("/api/v1/admin/users/{userId}")
  public Object save(
    @PathVariable UUID userId,
    @Valid @RequestBody UserInput body,
    Authentication auth,
    HttpServletRequest request
  ) {
    return identity.saveUser(
      id(auth),
      userId,
      body.login(),
      body.name(),
      body.roles(),
      body.active(),
      body.password(),
      correlation(request)
    );
  }

  @GetMapping("/api/v1/auth/audit")
  public Object audit(
    Authentication auth,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "desc") String direction
  ) {
    return identity.listAudit(id(auth), q, page, size, direction);
  }

  private static UUID id(Authentication auth) {
    return (UUID) auth.getPrincipal();
  }

  public record Credentials(
    @NotBlank @Size(max = 100) String login,
    @NotBlank @Size(max = 72) String password
  ) {}

  public record Registration(
    @NotBlank @Size(max = 100) String login,
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 72) String password
  ) {}

  public record Refresh(String refreshToken) {}

  public record Password(
    @NotBlank String currentPassword,
    @NotBlank String newPassword
  ) {}

  public record UserInput(
    @NotBlank @Size(max = 100) String login,
    @NotBlank @Size(max = 160) String name,
    @NotEmpty Set<String> roles,
    boolean active,
    @Size(max = 72) String password
  ) {}
}
