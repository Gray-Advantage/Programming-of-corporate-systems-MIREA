package space.grayt.teremok.catalog.library.security;

import org.springframework.security.core.Authentication;
import space.grayt.teremok.catalog.library.security.LibrarySecurity.Actor;

public final class RequestIdentity {

  private RequestIdentity() {}

  public static Actor actor(Authentication authentication) {
    return (Actor) authentication.getPrincipal();
  }
}
