package zpi.project_bird.backend.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;
import zpi.project_bird.backend.user.OrganizationRole;

@Service
public class OrganizationService {
    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<Organization> findAll() {
        return organizations.findAll();
    }

    public Organization lock(UUID organizationId) {
        return organizations.lock(organizationId).orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Organization was not found"));
    }

    @Transactional
    public Organization rename(UUID organizationId, String name) {
        lock(organizationId);
        return organizations.rename(organizationId, name.strip());
    }

    @Transactional
    public void delete(UUID organizationId) {
        lock(organizationId);
        organizations.delete(organizationId);
    }

    @Transactional
    public Membership addMembership(UUID organizationId, OrganizationRequest.AddMembership request,
            Authentication authentication) {
        Organization organization = lock(organizationId);
        requireOwnerAssignmentAuthority(request.membershipRole(), authentication);
        organizations.findUsername(request.userId()).orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "User was not found"));
        if (!organizations.addMembership(organizationId, request.userId(), request.membershipRole().toMembershipRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this organization");
        }
        return new Membership(organization, request.membershipRole().toMembershipRole());
    }

    @Transactional
    public Membership updateRole(UUID organizationId, String username, OrganizationRole role,
            Authentication authentication) {
        Organization organization = lock(organizationId);
        MembershipRole previous = organizations.findMembershipRole(organizationId, username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a member of this organization"));
        requireOwnerAssignmentAuthority(role, authentication);
        if (previous == MembershipRole._4_OWNER) {
            requireOwnerAssignmentAuthority(OrganizationRole.OWNER, authentication);
            if (role != OrganizationRole.OWNER) requireAnotherOwner(organizationId);
        }
        organizations.updateRole(organizationId, username, role.toMembershipRole());
        return new Membership(organization, role.toMembershipRole());
    }

    private void requireAnotherOwner(UUID organizationId) {
        if (organizations.ownerCount(organizationId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assign another owner before removing the last owner");
        }
    }

    public static void requireOwnerAssignmentAuthority(OrganizationRole role, Authentication authentication) {
        if (role == OrganizationRole.OWNER && !isSuperadmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only global administrators can assign or change owners");
        }
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
        return organizations.create(name.strip());
    }

    @Transactional
    public void removeFromOrganization(
            UUID organizationId,
            String username,
            Authentication remover) {
        lock(organizationId);
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

        if (targetRole == MembershipRole._4_OWNER) requireAnotherOwner(organizationId);

        if (!organizations.removeFromOrganization(organizationId, username, superadmin)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The user's organization membership changed; try again");
        }
    }

    private static boolean isSuperadmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
