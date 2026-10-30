package zpi.project_bird.backend.user;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RandomPasswordGenerator passwordGenerator;

    public UserService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            RandomPasswordGenerator passwordGenerator) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
    }

    @Transactional(readOnly = true)
    public List<User> findAll(UUID organizationId) {
        return users.findAll(organizationId);
    }

    @Transactional(readOnly = true)
    public List<User> findAllWithoutOrganization() {
        return users.findAllWithoutOrganization();
    }

    @Transactional
    public CreatedUser createAccount(
            UUID organizationId,
            UserRequest.CreateInOrganization request,
            Authentication creator) {
        MembershipRole role = isGlobalAdmin(creator)
            ? request.membershipRole().toMembershipRole()
            : MembershipRole._1_VIEWER;

        String password = passwordGenerator.generate();
        User user = users.createInOrganization(
            organizationId,
            request.friendlyName(),
            request.username(),
            passwordEncoder.encode(password),
            role);

        return new CreatedUser(user, password);
    }

    @Transactional
    public CreatedUser createWithoutOrganization(UserRequest.CreateWithoutOrganization request) {
        String password = passwordGenerator.generate();
        User user = users.createWithoutOrganization(
            request.friendlyName(),
            request.username(),
            passwordEncoder.encode(password),
            request.isAdmin());

        return new CreatedUser(user, password);
    }

    @Transactional(readOnly = true)
    public CurrentUser findCurrent(String username) {
        return users.findCurrentByUsername(username)
            .orElseThrow(UserService::currentUserNotFound);
    }

    @Transactional
    public CurrentUser updateFullName(String username, String fullName) {
        return users.updateFullName(username, fullName)
            .orElseThrow(UserService::currentUserNotFound);
    }

    @Transactional
    public CurrentUser updateFullName(
            UUID organizationId,
            String username,
            String fullName) {
        return users.updateFullName(organizationId, username, fullName)
            .orElseThrow(UserService::organizationUserNotFound);
    }

    @Transactional
    public void updatePassword(
            String username,
            String currentPassword,
            String newPassword) {
        String passwordHash = users.findPasswordHash(username)
            .orElseThrow(UserService::currentUserNotFound);
        if (!passwordEncoder.matches(currentPassword, passwordHash)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Current password is incorrect");
        }

        if (!users.updatePassword(username, passwordEncoder.encode(newPassword))) {
            throw currentUserNotFound();
        }
    }

    @Transactional
    public void updatePassword(
            UUID organizationId,
            String username,
            String newPassword) {
        if (!users.updatePassword(
                organizationId,
                username,
                passwordEncoder.encode(newPassword))) {
            throw organizationUserNotFound();
        }
    }

    private static boolean isGlobalAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private static ResponseStatusException currentUserNotFound() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Authenticated user no longer exists");
    }

    private static ResponseStatusException organizationUserNotFound() {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "User is not a member of this organization");
    }
}
