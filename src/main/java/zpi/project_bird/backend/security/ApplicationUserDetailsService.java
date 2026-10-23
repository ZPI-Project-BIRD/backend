package zpi.project_bird.backend.security;

import org.jooq.DSLContext;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import zpi.project_bird.backend.jooq.tables.records.UsersRecord;

import static zpi.project_bird.backend.jooq.tables.Users.USERS;

@Service
public class ApplicationUserDetailsService implements UserDetailsService {
    private final DSLContext dsl;

    public ApplicationUserDetailsService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        UsersRecord user = dsl.select().from(USERS).where(USERS.USERNAME.eq(username)).fetchOptionalInto(UsersRecord.class).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return User.withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(user.getIsAdmin() ? "ROLE_ADMIN" : "ROLE_USER")
            .disabled(user.getBannedAt() != null)
            .build();
    }
}
