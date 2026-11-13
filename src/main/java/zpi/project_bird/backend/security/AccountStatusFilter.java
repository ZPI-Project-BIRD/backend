package zpi.project_bird.backend.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import zpi.project_bird.backend.user.UserRepository;

public class AccountStatusFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountStatusFilter(UserRepository users) { this.users = users; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().equals("/api/auth/login") || request.getServletPath().equals("/api/auth/csrf");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            var state = users.sessionState(authentication.getName());
            int revision = authentication.getPrincipal() instanceof SessionUser user ? user.getAuthVersion() : 0;
            if (state.isEmpty() || state.get().banned() || state.get().version() != revision) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"account_session_revoked\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
