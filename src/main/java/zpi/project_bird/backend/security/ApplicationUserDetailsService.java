package zpi.project_bird.backend.security;

import org.jooq.DSLContext;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import static zpi.project_bird.backend.jooq.public_.tables.Users.USERS;

@Service
public class ApplicationUserDetailsService implements UserDetailsService {
    private final DSLContext dsl;

    public ApplicationUserDetailsService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        var version = org.jooq.impl.DSL.field(org.jooq.impl.DSL.name("auth_version"), Integer.class);
        var row = dsl.select(USERS.fields()).select(version).from(USERS).where(USERS.USERNAME.eq(username))
            .fetchOptional().orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new SessionUser(row.get(USERS.USERNAME), row.get(USERS.PASSWORD_HASH),
            row.get(USERS.IS_ADMIN), row.get(USERS.BANNED_AT) == null, row.get(version));
    }
}
