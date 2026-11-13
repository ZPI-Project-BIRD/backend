package zpi.project_bird.backend.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Carries the account revision in the persisted security context, so revoked sessions stay revoked after unban. */
public final class SessionUser extends User {
    private static final long serialVersionUID = 1L;
    private final int authVersion;

    public SessionUser(String username, String passwordHash, boolean admin, boolean enabled, int authVersion) {
        super(username, passwordHash, enabled, true, true, true,
            List.of(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER")));
        this.authVersion = authVersion;
    }

    public int getAuthVersion() { return authVersion; }
}
