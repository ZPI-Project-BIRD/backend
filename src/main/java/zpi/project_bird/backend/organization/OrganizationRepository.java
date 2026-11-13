package zpi.project_bird.backend.organization;

import static zpi.project_bird.backend.jooq.public_.tables.Memberships.MEMBERSHIPS;
import static zpi.project_bird.backend.jooq.public_.tables.Organizations.ORGANIZATIONS;
import static zpi.project_bird.backend.jooq.public_.tables.Users.USERS;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;
import zpi.project_bird.backend.jooq.public_.tables.records.OrganizationsRecord;

@Repository
public class OrganizationRepository {
    private final DSLContext dsl;

    public OrganizationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Organization> findAll() {
        return dsl.selectFrom(ORGANIZATIONS).orderBy(ORGANIZATIONS.NAME, ORGANIZATIONS.ID)
            .fetch(row -> new Organization(row.getId(), row.getName()));
    }

    public Optional<Organization> lock(UUID organizationId) {
        return dsl.selectFrom(ORGANIZATIONS).where(ORGANIZATIONS.ID.eq(organizationId))
            .forUpdate().fetchOptional(row -> new Organization(row.getId(), row.getName()));
    }

    public Organization rename(UUID organizationId, String name) {
        var row = dsl.update(ORGANIZATIONS).set(ORGANIZATIONS.NAME, name)
            .where(ORGANIZATIONS.ID.eq(organizationId)).returning().fetchSingle();
        return new Organization(row.getId(), row.getName());
    }

    public void delete(UUID organizationId) {
        // Projects and their processing/annotation records cascade; shared imagery and accounts remain.
        dsl.deleteFrom(MEMBERSHIPS).where(MEMBERSHIPS.ORGANIZATION_ID.eq(organizationId)).execute();
        dsl.deleteFrom(ORGANIZATIONS).where(ORGANIZATIONS.ID.eq(organizationId)).execute();
    }

    public Optional<String> findUsername(UUID userId) {
        return dsl.select(USERS.USERNAME).from(USERS).where(USERS.ID.eq(userId))
            .fetchOptional(USERS.USERNAME);
    }

    public boolean addMembership(UUID organizationId, UUID userId, MembershipRole role) {
        return dsl.insertInto(MEMBERSHIPS).set(MEMBERSHIPS.ORGANIZATION_ID, organizationId)
            .set(MEMBERSHIPS.USER_ID, userId).set(MEMBERSHIPS.ROLE, role)
            .onConflictDoNothing().execute() == 1;
    }

    public void updateRole(UUID organizationId, String username, MembershipRole role) {
        dsl.update(MEMBERSHIPS).set(MEMBERSHIPS.ROLE, role)
            .where(MEMBERSHIPS.ORGANIZATION_ID.eq(organizationId))
            .and(MEMBERSHIPS.USER_ID.eq(dsl.select(USERS.ID).from(USERS)
                .where(USERS.USERNAME.eq(username)))).execute();
    }

    public int ownerCount(UUID organizationId) {
        return dsl.fetchCount(MEMBERSHIPS, MEMBERSHIPS.ORGANIZATION_ID.eq(organizationId)
            .and(MEMBERSHIPS.ROLE.eq(MembershipRole._4_OWNER)));
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
        return this.findByUsername(username).stream()
            .filter(Membership::canOwn)
            .map(Membership::organization)
            .collect(Collectors.toList());
    }

    public List<Organization> findEditableByUsername(String username) {
        return this.findByUsername(username).stream()
            .filter(Membership::canEdit)
            .map(Membership::organization)
            .collect(Collectors.toList());
    }

    public Organization create(String name) {
        OrganizationsRecord record = dsl.insertInto(ORGANIZATIONS)
            .set(ORGANIZATIONS.NAME, name)
            .returning()
            .fetchSingle();

        return new Organization(record.getId(), record.getName());
    }

    public Optional<MembershipRole> findMembershipRole(UUID organizationId, String username) {
        return dsl.select(MEMBERSHIPS.ROLE)
            .from(MEMBERSHIPS)
            .join(USERS).on(USERS.ID.eq(MEMBERSHIPS.USER_ID))
            .where(
                MEMBERSHIPS.ORGANIZATION_ID.eq(organizationId),
                USERS.USERNAME.eq(username))
            .fetchOptional(MEMBERSHIPS.ROLE);
    }

    public boolean removeFromOrganization(
            UUID organizationId,
            String username,
            boolean canRemoveOwner) {
        Condition targetMembership = MEMBERSHIPS.ORGANIZATION_ID.eq(organizationId)
            .and(MEMBERSHIPS.USER_ID.eq(
                dsl.select(USERS.ID)
                    .from(USERS)
                    .where(USERS.USERNAME.eq(username))));

        if (!canRemoveOwner) {
            targetMembership = targetMembership.and(
                MEMBERSHIPS.ROLE.ne(MembershipRole._4_OWNER));
        }

        return dsl.deleteFrom(MEMBERSHIPS)
            .where(targetMembership)
            .execute() > 0;
    }
}
