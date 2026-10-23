package zpi.project_bird.backend.user;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/organizations/{organizationId}/users")
    @CanManageOrganization
    public ResponseEntity<CreatedUser> createInOrganization(
            @PathVariable UUID organizationId,
            @Valid @RequestBody UserRequest.CreateInOrganization request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.createInOrganization(organizationId, request, authentication));
    }

    @PostMapping("/organizations/null/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreatedUser> createWithoutOrganization(
            @Valid @RequestBody UserRequest.CreateWithoutOrganization request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.createWithoutOrganization(request));
    }
}
