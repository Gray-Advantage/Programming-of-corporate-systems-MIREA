package space.grayt.teremok.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.grayt.teremok.auth.domain.ManagedUser;
import space.grayt.teremok.auth.messaging.UserCreatedEventPublisher;
import space.grayt.teremok.auth.repository.ManagedUsers;
import space.grayt.teremok.auth.security.JwtService;
import space.grayt.teremok.events.UserCreatedEvent;

@Service
public class IdentityService implements ApplicationRunner {

  private final ManagedUsers users;
  private final JdbcTemplate db;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  private final UserCreatedEventPublisher events;
  private final String adminLogin, adminPassword;

  public IdentityService(
    ManagedUsers users,
    JdbcTemplate db,
    PasswordEncoder passwords,
    JwtService jwt,
    UserCreatedEventPublisher events,
    @Value("${BOOTSTRAP_ADMIN_LOGIN:admin}") String adminLogin,
    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword
  ) {
    this.users = users;
    this.db = db;
    this.passwords = passwords;
    this.jwt = jwt;
    this.events = events;
    this.adminLogin = adminLogin;
    this.adminPassword = adminPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (
      !adminPassword.isBlank() && users.findByLogin(adminLogin).isEmpty()
    ) create(adminLogin, "Администратор", adminPassword, Set.of("ADMIN"), true);
  }

  public static void passwordPolicy(String password) {
    if (
      password == null ||
      password.length() < 10 ||
      password.getBytes(StandardCharsets.UTF_8).length > 72 ||
      !password.matches(".*[A-ZА-Я].*") ||
      !password.matches(".*[a-zа-я].*") ||
      !password.matches(".*[0-9].*")
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Пароль: от 10 символов, прописная и строчная буквы, цифра; до 72 байт"
    );
  }

  @Transactional
  public Profile register(String login, String name, String password) {
    passwordPolicy(password);
    return profile(create(login, name, password, Set.of("EDITOR"), false));
  }

  @Transactional
  public Session registerSession(
    String login,
    String name,
    String password,
    String remote,
    String correlation
  ) {
    register(login, name, password);
    return login(login, password, remote, correlation);
  }

  @Transactional
  public space.grayt.teremok.client.contract.AuthTokenResponse registerLegacy(
    String login,
    String password,
    String remote,
    String correlation
  ) {
    return legacyToken(
      registerSession(login, login, password, remote, correlation)
    );
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public space.grayt.teremok.client.contract.AuthTokenResponse loginLegacy(
    String login,
    String password,
    String remote,
    String correlation
  ) {
    return legacyToken(login(login, password, remote, correlation));
  }

  private static space.grayt.teremok.client.contract.AuthTokenResponse legacyToken(
    Session session
  ) {
    return new space.grayt.teremok.client.contract.AuthTokenResponse(
      session.user().id(),
      session.accessToken(),
      "Bearer",
      session.expiresAt()
    );
  }

  private ManagedUser create(
    String login,
    String name,
    String password,
    Set<String> roles,
    boolean temporary
  ) {
    if (login == null || login.isBlank() || login.trim().length() > 100) bad(
      "Укажите логин до 100 символов"
    );
    if (
      users.findByLogin(login.trim()).isPresent()
    ) throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин уже занят");
    var user = new ManagedUser();
    user.id = UUID.randomUUID();
    user.login = login.trim();
    user.name = name == null ? user.login : name;
    user.passwordHash = passwords.encode(password);
    user.registeredAt = Instant.now();
    user.active = true;
    user.mustChangePassword = temporary;
    user.roles = new HashSet<>(roles);
    users.saveAndFlush(user);
    events.publish(
      new UserCreatedEvent(
        UUID.randomUUID(),
        1,
        user.registeredAt,
        new UserCreatedEvent.UserPayload(user.id, user.registeredAt)
      )
    );
    return user;
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Session login(
    String login,
    String password,
    String remote,
    String correlation
  ) {
    if (
      login == null ||
      login.isBlank() ||
      login.length() > 100 ||
      password == null ||
      password.getBytes(StandardCharsets.UTF_8).length > 72
    ) bad("Проверьте формат логина и пароля");
    var key = (remote + ":" + Objects.toString(login, "")).substring(
      0,
      Math.min(180, (remote + ":" + Objects.toString(login, "")).length())
    );
    Integer locked = db.queryForObject(
      "SELECT COUNT(*) FROM login_attempt WHERE attempt_key=? AND failures>=5 AND locked_until>now()",
      Integer.class,
      key
    );
    if (locked != null && locked > 0) throw new ResponseStatusException(
      HttpStatus.TOO_MANY_REQUESTS,
      "Слишком много попыток. Повторите через 15 минут"
    );
    var found =
      login == null
        ? Optional.<ManagedUser>empty()
        : users.findByLogin(login.trim());
    if (
      found.isEmpty() ||
      !found.get().active ||
      password == null ||
      !passwords.matches(password, found.get().passwordHash)
    ) {
      db.update(
        "INSERT INTO login_attempt VALUES (?,1,now()+interval '15 minutes') ON CONFLICT(attempt_key) DO UPDATE SET failures=CASE WHEN login_attempt.locked_until<now() THEN 1 ELSE login_attempt.failures+1 END, locked_until=now()+interval '15 minutes'",
        key
      );
      audit(
        null,
        Objects.toString(login, ""),
        "LOGIN_FAILED",
        null,
        "Неудачный вход",
        correlation
      );
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Неверный логин или пароль"
      );
    }
    db.update("DELETE FROM login_attempt WHERE attempt_key=?", key);
    var user = found.get();
    audit(user.id, user.login, "LOGIN", user.id, "Вход в систему", correlation);
    return session(user);
  }

  private Session session(ManagedUser user) {
    var token = UUID.randomUUID() + "." + UUID.randomUUID();
    db.update(
      "INSERT INTO refresh_session(id,user_id,token_hash,expires_at) VALUES(?,?,?,?)",
      UUID.randomUUID(),
      user.id,
      hash(token),
      java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(14)))
    );
    var access = jwt.issue(user.id);
    return new Session(
      access.value(),
      access.expiresAt(),
      token,
      profile(user)
    );
  }

  @Transactional
  public Session refresh(String token) {
    var ids = db.query(
      "SELECT user_id FROM refresh_session WHERE token_hash=? AND NOT revoked AND expires_at>now() FOR UPDATE",
      (r, n) -> r.getObject(1, UUID.class),
      hash(token)
    );
    if (ids.isEmpty()) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Сеанс завершён. Войдите заново"
    );
    db.update(
      "UPDATE refresh_session SET revoked=true WHERE token_hash=?",
      hash(token)
    );
    var user = required(ids.getFirst());
    if (!user.active) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Учётная запись отключена"
    );
    return session(user);
  }

  @Transactional
  public void logout(String token, UUID actor, String correlation) {
    if (token != null) db.update(
      "UPDATE refresh_session SET revoked=true WHERE token_hash=?",
      hash(token)
    );
    audit(
      actor,
      actor == null ? "" : required(actor).login,
      "LOGOUT",
      actor,
      "Выход из системы",
      correlation
    );
  }

  public ManagedUser required(UUID id) {
    return users
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Пользователь не найден"
        )
      );
  }

  public Profile me(UUID id) {
    var user = required(id);
    if (!user.active) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Учётная запись отключена"
    );
    return profile(user);
  }

  public void admin(UUID id) {
    var user = required(id);
    if (
      !user.active || user.mustChangePassword || !user.roles.contains("ADMIN")
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Требуются права администратора"
    );
  }

  @Transactional
  public void changePassword(
    UUID id,
    String current,
    String next,
    String correlation
  ) {
    passwordPolicy(next);
    var user = required(id);
    if (
      !passwords.matches(current, user.passwordHash)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Текущий пароль неверен"
    );
    if (passwords.matches(next, user.passwordHash)) bad(
      "Новый пароль должен отличаться от текущего"
    );
    user.passwordHash = passwords.encode(next);
    user.mustChangePassword = false;
    users.saveAndFlush(user);
    db.update("UPDATE refresh_session SET revoked=true WHERE user_id=?", id);
    audit(
      id,
      user.login,
      "PASSWORD_CHANGED",
      id,
      "Изменён пароль",
      correlation
    );
  }

  @Transactional
  public Profile saveUser(
    UUID actor,
    UUID id,
    String login,
    String name,
    Set<String> roles,
    boolean active,
    String password,
    String correlation
  ) {
    admin(actor);
    if (
      roles == null ||
      roles.isEmpty() ||
      !Set.of("READER", "EDITOR", "ADMIN").containsAll(roles)
    ) bad("Выберите допустимые роли");
    if (
      id != null && id.equals(actor) && (!active || !roles.contains("ADMIN"))
    ) bad("Нельзя отключить собственные права администратора");
    if (id == null) {
      passwordPolicy(password);
      var created = create(login, name, password, roles, true);
      audit(
        actor,
        required(actor).login,
        "USER_CREATED",
        created.id,
        "Создан пользователь",
        correlation
      );
      return profile(created);
    }
    var user = required(id);
    var duplicate = users.findByLogin(login.trim());
    if (
      duplicate.isPresent() && !duplicate.get().id.equals(id)
    ) throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин уже занят");
    user.login = login.trim();
    user.name = name;
    user.roles = new HashSet<>(roles);
    user.active = active;
    if (password != null && !password.isBlank()) {
      passwordPolicy(password);
      user.passwordHash = passwords.encode(password);
      user.mustChangePassword = true;
    }
    users.saveAndFlush(user);
    db.update("UPDATE refresh_session SET revoked=true WHERE user_id=?", id);
    audit(
      actor,
      required(actor).login,
      "USER_UPDATED",
      id,
      "Изменён пользователь",
      correlation
    );
    return profile(user);
  }

  public void audit(
    UUID id,
    String login,
    String type,
    UUID entity,
    String text,
    String correlation
  ) {
    db.update(
      "INSERT INTO auth_audit VALUES(?,?,?,?,?,?,?,?,?)",
      UUID.randomUUID(),
      id,
      login,
      type,
      "USER",
      entity,
      java.sql.Timestamp.from(Instant.now()),
      text,
      correlation
    );
  }

  public static Profile profile(ManagedUser user) {
    return new Profile(
      user.id,
      user.login,
      user.name.isBlank() ? user.login : user.name,
      user.active,
      user.mustChangePassword,
      Set.copyOf(user.roles)
    );
  }

  private static String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          Objects.toString(value, "").getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void bad(String text) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
  }

  public record Profile(
    UUID id,
    String login,
    String name,
    boolean active,
    boolean mustChangePassword,
    Set<String> roles
  ) {}

  public record Session(
    String accessToken,
    Instant expiresAt,
    String refreshToken,
    Profile user
  ) {}

  @Transactional(readOnly = true)
  public Object listUsers(UUID actor, String q, int page, int size) {
    admin(actor);
    return users
      .findByLoginContainingIgnoreCaseOrNameContainingIgnoreCase(
        q,
        q,
        PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100))
      )
      .map(IdentityService::profile);
  }

  @Transactional(readOnly = true)
  public Object listAudit(
    UUID actor,
    String q,
    int page,
    int size,
    String direction
  ) {
    admin(actor);
    int limit = Math.clamp(size, 1, 100);
    String search = "%" + q.toLowerCase(Locale.ROOT) + "%";
    return Map.of(
      "content",
      db.queryForList(
        "SELECT * FROM auth_audit WHERE lower(actor||' '||event_type||' '||description) LIKE ? ORDER BY occurred_at " +
          ("asc".equals(direction) ? "ASC" : "DESC") +
          " LIMIT ? OFFSET ?",
        search,
        limit,
        (long) Math.max(0, page) * limit
      ),
      "totalElements",
      db.queryForObject(
        "SELECT count(*) FROM auth_audit WHERE lower(actor||' '||event_type||' '||description) LIKE ?",
        Long.class,
        search
      )
    );
  }
}
