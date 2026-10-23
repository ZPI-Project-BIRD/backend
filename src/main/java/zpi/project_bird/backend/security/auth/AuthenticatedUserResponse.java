package zpi.project_bird.backend.security.auth;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record AuthenticatedUserResponse(String username, Set<String> authorities) {
    static AuthenticatedUserResponse from(Authentication authentication) {
        var authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toUnmodifiableSet());

        return new AuthenticatedUserResponse(authentication.getName(), authorities);
    }
}
