package space.grayt.teremok.auth.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  @Test
  void rejectsWeakPasswordsAndBcryptTruncation() {
    for (String password : new String[] {
      "short",
      "alllowercase12",
      "ALLUPPERCASE12",
      "NoDigitsHere",
      "Ж".repeat(40) + "a1",
    })
      assertThatThrownBy(() ->
        IdentityService.passwordPolicy(password)
      ).isInstanceOf(
        org.springframework.web.server.ResponseStatusException.class
      );
    assertThatCode(() ->
      IdentityService.passwordPolicy("НадёжныйПароль123")
    ).doesNotThrowAnyException();
  }
}
