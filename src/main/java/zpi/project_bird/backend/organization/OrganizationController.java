package zpi.project_bird.backend.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import zpi.project_bird.backend.security.CanManageOrganization;
import zpi.project_bird.backend.user.CreatedUser;
import zpi.project_bird.backend.user.CurrentUser;
import zpi.project_bird.backend.user.UserRequest;
import zpi.project_bird.backend.user.UserService;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;
    private final UserService userService;

    public OrganizationController(
            OrganizationService organizationService,
            UserService userService) {
        this.organizationService = organizationService;
        this.userService = userService;
    }

    @GetMapping("/my")
    public List<Membership> findMyOrganizations(Authentication authentication) {
        return organizationService.findByUsername(authentication.getName());
    }

    @GetMapping("/owned")
    public List<Organization> findOwned(Authentication authentication) {
        return organizationService.findOwnedByUsername(authentication.getName());
    }

    @GetMapping("/editable")
    public List<Organization> findEditable(Authentication authentication) {
        return organizationService.findEditableByUsername(authentication.getName());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Organization> createOrganization(
            @Valid @RequestBody OrganizationRequest.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(organizationService.create(request.name()));
    }

    @PostMapping("/{organizationId}/users")
    @CanManageOrganization
    public ResponseEntity<CreatedUser> createAccount(
            @PathVariable UUID organizationId,
            @Valid @RequestBody UserRequest.CreateInOrganization request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.createAccount(organizationId, request, authentication));
    }

    @PatchMapping("/{organizationId}/users/{username}/full-name")
    @PreAuthorize("hasRole('ADMIN')")
    public CurrentUser updateUserFullName(
            @PathVariable UUID organizationId,
            @PathVariable String username,
            @Valid @RequestBody UserRequest.UpdateFullName request) {
        return userService.updateFullName(organizationId, username, request.fullName());
    }

    @PatchMapping("/{organizationId}/users/{username}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateUserPassword(
            @PathVariable UUID organizationId,
            @PathVariable String username,
            @Valid @RequestBody UserRequest.UpdatePassword request) {
        userService.updatePassword(organizationId, username, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{organizationId}/users/{username}")
    @CanManageOrganization
    public ResponseEntity<Void> removeUser(
            @PathVariable UUID organizationId,
            @PathVariable String username,
            Authentication authentication) {
        organizationService.removeFromOrganization(organizationId, username, authentication);
        return ResponseEntity.noContent().build();
    }
}
