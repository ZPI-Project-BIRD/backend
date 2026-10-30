package zpi.project_bird.backend.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;

@Service
public class OrganizationService {
    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<Membership> findByUsername(String username) {
        return organizations.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<Organization> findOwnedByUsername(String username) {
        return organizations.findOwnedByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<Organization> findEditableByUsername(String username) {
        return organizations.findEditableByUsername(username);
    }

    @Transactional
    public Organization create(String name) {
        return organizations.create(name);
    }

    @Transactional
    public void removeFromOrganization(
            UUID organizationId,
            String username,
            Authentication remover) {
        if (remover.getName().equals(username)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Users cannot remove themselves from an organization");
        }

        MembershipRole targetRole = organizations.findMembershipRole(organizationId, username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User is not a member of this organization"));

        boolean superadmin = isSuperadmin(remover);
        if (targetRole == MembershipRole._4_OWNER && !superadmin) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only superadmins can remove organization owners");
        }

        if (!organizations.removeFromOrganization(organizationId, username, superadmin)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The user's organization membership changed; try again");
        }
    }

    private static boolean isSuperadmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
