package zpi.project_bird.backend.user;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import zpi.project_bird.backend.security.CanManageOrganization;

@RestController
@RequestMapping("/api")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> search(@RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return userService.search(search, limit, offset);
    }

    @PostMapping("/users/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> ban(@PathVariable UUID userId,
            @Valid @RequestBody UserRequest.Ban request, Authentication authentication) {
        userService.ban(userId, request.reason(), authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unban(@PathVariable UUID userId) {
        userService.unban(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/organizations/{organizationId}/users")
    @CanManageOrganization
    public List<User> findAll(@PathVariable UUID organizationId) {
        return userService.findAll(organizationId);
    }

    @GetMapping("/organizations/null/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> findAllWithoutOrganization() {
        return userService.findAllWithoutOrganization();
    }

    @PostMapping("/organizations/null/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreatedUser> createWithoutOrganization(
            @Valid @RequestBody UserRequest.CreateWithoutOrganization request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.createWithoutOrganization(request));
    }
}
