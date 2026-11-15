package occupan.data;

import java.time.Instant;
import java.util.Optional;
import occupan.data.Database.Account;
import occupan.data.Database.Session;
import occupan.domain.User;

/** Data access layer: users and sessions. */
public final class UserRepository {
    private final Database db;

    public UserRepository(Database db) { this.db = db; }

    public void insert(Account account) {
        synchronized (db.lock) {
            db.accountsByUsername.put(account.user().username(), account);
            db.usersById.put(account.user().userId(), account.user());
        }
    }

    public int count() {
        synchronized (db.lock) { return db.accountsByUsername.size(); }
    }

    public Optional<Account> findByUsername(String username) {
        synchronized (db.lock) { return Optional.ofNullable(db.accountsByUsername.get(username)); }
    }

    public Optional<User> findById(String userId) {
        synchronized (db.lock) { return Optional.ofNullable(db.usersById.get(userId)); }
    }

    public void createSession(Session session) { db.sessions.put(session.token(), session); }

    public Optional<User> userForToken(String token, Instant now) {
        Session session = token == null ? null : db.sessions.get(token);
        if (session == null) return Optional.empty();
        if (!session.expiresAt().isAfter(now)) {
            db.sessions.remove(token);
            return Optional.empty();
        }
        return findById(session.userId());
    }

    public void deleteSession(String token) {
        if (token != null) db.sessions.remove(token);
    }
}
