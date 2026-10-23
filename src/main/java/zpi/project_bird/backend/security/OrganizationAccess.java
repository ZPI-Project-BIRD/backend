package zpi.project_bird.backend.security;

import org.jooq.DSLContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import zpi.project_bird.backend.jooq.enums.MembershipRole;

import static zpi.project_bird.backend.jooq.Tables.MEMBERSHIPS;
import static zpi.project_bird.backend.jooq.tables.Users.USERS;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;
import java.util.stream.Collectors;

@Component("orgAccess")
public class OrganizationAccess {
    private final DSLContext dsl;

    public OrganizationAccess(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean canManage(
        Authentication authentication,
        UUID organizationId
    ) {
        return hasAnyRole(
            authentication,
            organizationId,
            MembershipRole._4_OWNER
        );
    }

    public boolean canAnnotate(Authentication authentication, UUID organizationId) {
        return hasAnyRole(
            authentication,
            organizationId,
            MembershipRole._4_OWNER,
            MembershipRole._3_EDITOR,
            MembershipRole._2_ANNOTATOR
        );
    }

    public boolean canEdit(Authentication authentication, UUID organizationId) {
        return hasAnyRole(
            authentication,
            organizationId,
            MembershipRole._4_OWNER,
            MembershipRole._3_EDITOR
        );
    }

    public boolean canView(Authentication authentication, UUID organizationId) {
        return hasAnyRole(
            authentication,
            organizationId,
            MembershipRole._4_OWNER,
            MembershipRole._3_EDITOR,
            MembershipRole._2_ANNOTATOR,
            MembershipRole._1_VIEWER
        );
    }

    private boolean hasAnyRole(Authentication authentication, UUID organizationId, MembershipRole... roles) {
        if (authentication == null || authentication.getPrincipal() == null || organizationId == null || roles == null) {
            return false;
        }

        User user = (User) authentication.getPrincipal();
        if (user.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet()).contains("ROLE_ADMIN")) {
            return true;
        }

        return dsl.select(USERS.ID).from(USERS)
            .leftJoin(MEMBERSHIPS).on(USERS.ID.eq(MEMBERSHIPS.USER_ID))
            .where(USERS.USERNAME.eq(authentication.getName()), MEMBERSHIPS.ORGANIZATION_ID.eq(organizationId), MEMBERSHIPS.ROLE.in(roles))
            .fetchOptional().isPresent();
    }
}

