package space.grayt.teremok.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "user_account")
public class ManagedUser {

  @Id
  public UUID id;

  @Column(nullable = false, length = 100)
  public String login;

  @Column(name = "password_hash", nullable = false, length = 100)
  public String passwordHash;

  @Column(name = "registered_at", nullable = false)
  public Instant registeredAt;

  @Column(name = "display_name", nullable = false, length = 160)
  public String name;

  public boolean active;

  @Column(name = "must_change_password")
  public boolean mustChangePassword;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
    name = "user_role",
    joinColumns = @JoinColumn(name = "user_id")
  )
  @Column(name = "role", length = 16)
  public Set<String> roles = new HashSet<>();
}
