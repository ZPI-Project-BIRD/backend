package zpi.project_bird.backend.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public CurrentUser findCurrent(Authentication authentication) {
        return userService.findCurrent(authentication.getName());
    }

    @PatchMapping("/full-name")
    public CurrentUser updateFullName(
            Authentication authentication,
            @Valid @RequestBody MeRequest.UpdateFullName request) {
        return userService.updateFullName(authentication.getName(), request.fullName());
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> updatePassword(
            Authentication authentication,
            @Valid @RequestBody MeRequest.UpdatePassword request) {
        userService.updatePassword(
            authentication.getName(),
            request.currentPassword(),
            request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
