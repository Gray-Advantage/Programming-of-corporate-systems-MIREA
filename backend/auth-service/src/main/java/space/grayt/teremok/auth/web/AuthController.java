package space.grayt.teremok.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import space.grayt.teremok.auth.service.IdentityService;
import space.grayt.teremok.client.contract.*;
import space.grayt.teremok.http.ApiErrors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final IdentityService identity;

  public AuthController(IdentityService identity) {
    this.identity = identity;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthTokenResponse> register(
    @RequestBody AuthCredentialsRequest body,
    HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CREATED).body(
      identity.registerLegacy(
        body.login(),
        body.password(),
        request.getRemoteAddr(),
        ApiErrors.correlation(request)
      )
    );
  }

  @PostMapping("/login")
  public AuthTokenResponse login(
    @RequestBody AuthCredentialsRequest body,
    HttpServletRequest request
  ) {
    return identity.loginLegacy(
      body.login(),
      body.password(),
      request.getRemoteAddr(),
      ApiErrors.correlation(request)
    );
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent().build();
  }
}
