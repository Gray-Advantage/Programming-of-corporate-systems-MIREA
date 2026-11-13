package space.grayt.teremok.auth.repository;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import space.grayt.teremok.auth.domain.ManagedUser;

public interface ManagedUsers extends JpaRepository<ManagedUser, UUID> {
  Optional<ManagedUser> findByLogin(String login);
  Page<ManagedUser> findByLoginContainingIgnoreCaseOrNameContainingIgnoreCase(
    String login,
    String name,
    Pageable page
  );
}
