package occupan.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import occupan.Clock;
import occupan.Config;
import occupan.data.Database.Account;
import occupan.data.Database.Session;
import occupan.data.UserRepository;
import occupan.domain.DomainException;
import occupan.domain.Role;
import occupan.domain.User;

/** Application layer: login, sessions and password handling (FR-1). */
public final class AuthService {
    private static final int ITERATIONS = 100_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record LoginResult(String token, User user) {}

    private final UserRepository users;

    public AuthService(UserRepository users) { this.users = users; }

    static String hashPassword(String password, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(saltHex),
                    ITERATIONS, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing is not available", e);
        }
    }

    public User createUser(String username, String name, Role role, String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        User user = new User(UUID.randomUUID().toString(), username, name, role);
        users.insert(new Account(user, saltHex, hashPassword(password, saltHex)));
        return user;
    }

    /** Checks credentials and opens a session. Same error for an unknown user and a wrong password. */
    public LoginResult login(String username, String password) {
        Account account = users.findByUsername(username == null ? "" : username.strip()).orElse(null);
        String given = account == null ? "" : hashPassword(password == null ? "" : password, account.salt());
        boolean ok = account != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), account.passwordHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) throw DomainException.authentication("Invalid username or password.");
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        users.createSession(new Session(token, account.user().userId(),
                Clock.now().plus(Config.sessionHours(), ChronoUnit.HOURS)));
        return new LoginResult(token, account.user());
    }

    public User userForToken(String token) {
        return users.userForToken(token, Clock.now())
                .orElseThrow(() -> DomainException.authentication("Please sign in again."));
    }

    public void logout(String token) { users.deleteSession(token); }
}
