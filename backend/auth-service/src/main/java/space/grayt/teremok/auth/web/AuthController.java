package space.grayt.teremok.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.auth.service.AuthService;
import space.grayt.teremok.client.contract.AuthCredentialsRequest;
import space.grayt.teremok.client.contract.AuthTokenResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthTokenResponse> register(@RequestBody AuthCredentialsRequest request) {
        var response = toResponse(authService.register(request.login(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@RequestBody AuthCredentialsRequest request) {
        return toResponse(authService.login(request.login(), request.password()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    private static AuthTokenResponse toResponse(AuthService.AuthResult result) {
        return new AuthTokenResponse(result.userId(), result.accessToken(), "Bearer", result.expiresAt());
    }
}
