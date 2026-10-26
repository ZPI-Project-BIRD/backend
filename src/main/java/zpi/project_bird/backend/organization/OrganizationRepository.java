package zpi.project_bird.backend.organization;

import static zpi.project_bird.backend.jooq.public_.tables.Memberships.MEMBERSHIPS;
import static zpi.project_bird.backend.jooq.public_.tables.Organizations.ORGANIZATIONS;
import static zpi.project_bird.backend.jooq.public_.tables.Users.USERS;

import java.util.List;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;

@Repository
public class OrganizationRepository {
    private final DSLContext dsl;

    public OrganizationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Membership> findByUsername(String username) {
        return dsl.select(ORGANIZATIONS.ID, ORGANIZATIONS.NAME, MEMBERSHIPS.ROLE)
            .from(ORGANIZATIONS)
            .join(MEMBERSHIPS).on(MEMBERSHIPS.ORGANIZATION_ID.eq(ORGANIZATIONS.ID))
            .join(USERS).on(USERS.ID.eq(MEMBERSHIPS.USER_ID))
            .where(USERS.USERNAME.eq(username))
            .orderBy(ORGANIZATIONS.ID)
            .fetch(record -> new Membership(
                new Organization(record.get(ORGANIZATIONS.ID),record.get(ORGANIZATIONS.NAME)),
                record.get(MEMBERSHIPS.ROLE)));
    }

    public List<Organization> findOwnedByUsername(String username) {
        return dsl.select(ORGANIZATIONS.ID, ORGANIZATIONS.NAME)
            .from(ORGANIZATIONS)
            .join(MEMBERSHIPS).on(MEMBERSHIPS.ORGANIZATION_ID.eq(ORGANIZATIONS.ID))
            .join(USERS).on(USERS.ID.eq(MEMBERSHIPS.USER_ID))
            .where(
                USERS.USERNAME.eq(username),
                MEMBERSHIPS.ROLE.eq(MembershipRole._4_OWNER))
            .orderBy(ORGANIZATIONS.ID)
            .fetch(record -> new Organization(
                record.get(ORGANIZATIONS.ID),
                record.get(ORGANIZATIONS.NAME)));
    }
}
