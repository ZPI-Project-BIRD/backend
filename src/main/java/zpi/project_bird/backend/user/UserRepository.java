package zpi.project_bird.backend.user;

import static zpi.project_bird.backend.jooq.public_.tables.Memberships.MEMBERSHIPS;
import static zpi.project_bird.backend.jooq.public_.tables.Users.USERS;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;
import zpi.project_bird.backend.jooq.public_.tables.records.UsersRecord;

@Repository
public class UserRepository {
    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<User> findAll(UUID organizationId) {
        return dsl.select(
                USERS.ID,
                USERS.FRIENDLY_NAME,
                USERS.USERNAME,
                USERS.IS_ADMIN,
                USERS.BANNED_AT,
                USERS.BAN_REASON,
                MEMBERSHIPS.ROLE)
            .from(USERS)
            .join(MEMBERSHIPS).on(MEMBERSHIPS.USER_ID.eq(USERS.ID))
            .where(MEMBERSHIPS.ORGANIZATION_ID.eq(organizationId))
            .orderBy(USERS.ID)
            .fetch(record -> toUser(record, record.get(MEMBERSHIPS.ROLE)));
    }

    public List<User> findAllWithoutOrganization() {
        return dsl.selectFrom(USERS)
            .whereNotExists(
                dsl.selectOne()
                    .from(MEMBERSHIPS)
                    .where(MEMBERSHIPS.USER_ID.eq(USERS.ID)))
            .orderBy(USERS.ID)
            .fetch(record -> toUser(record, null));
    }

    public User createInOrganization(
            UUID organizationId,
            String friendlyName,
            String username,
            String passwordHash,
            MembershipRole membershipRole) {
        UsersRecord record = createUser(friendlyName, username, passwordHash, false);

        dsl.insertInto(MEMBERSHIPS)
            .set(MEMBERSHIPS.USER_ID, record.getId())
            .set(MEMBERSHIPS.ORGANIZATION_ID, organizationId)
            .set(MEMBERSHIPS.ROLE, membershipRole)
            .execute();

        return toUser(record, membershipRole);
    }

    public User createWithoutOrganization(
            String friendlyName,
            String username,
            String passwordHash,
            boolean isAdmin) {
        return toUser(createUser(friendlyName, username, passwordHash, isAdmin), null);
    }

    public Optional<CurrentUser> findCurrentByUsername(String username) {
        return dsl.select(
                USERS.ID,
                USERS.FRIENDLY_NAME,
                USERS.USERNAME,
                USERS.IS_ADMIN)
            .from(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOptional(record -> new CurrentUser(
                record.get(USERS.ID),
                record.get(USERS.FRIENDLY_NAME),
                record.get(USERS.USERNAME),
                record.get(USERS.IS_ADMIN)));
    }

    public Optional<CurrentUser> updateFullName(String username, String fullName) {
        return dsl.update(USERS)
            .set(USERS.FRIENDLY_NAME, fullName)
            .where(USERS.USERNAME.eq(username))
            .returning(
                USERS.ID,
                USERS.FRIENDLY_NAME,
                USERS.USERNAME,
                USERS.IS_ADMIN)
            .fetchOptional(record -> new CurrentUser(
                record.get(USERS.ID),
                record.get(USERS.FRIENDLY_NAME),
                record.get(USERS.USERNAME),
                record.get(USERS.IS_ADMIN)));
    }

    public Optional<String> findPasswordHash(String username) {
        return dsl.select(USERS.PASSWORD_HASH)
            .from(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOptional(USERS.PASSWORD_HASH);
    }

    public boolean updatePassword(String username, String passwordHash) {
        return dsl.update(USERS)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .where(USERS.USERNAME.eq(username))
            .execute() == 1;
    }

    private UsersRecord createUser(
            String friendlyName,
            String username,
            String passwordHash,
            boolean isAdmin) {
        return dsl.insertInto(USERS)
            .set(USERS.FRIENDLY_NAME, friendlyName)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.IS_ADMIN, isAdmin)
            .returning()
            .fetchSingle();
    }

    private static User toUser(Record record, MembershipRole membershipRole) {
        return new User(
            record.get(USERS.ID),
            record.get(USERS.FRIENDLY_NAME),
            record.get(USERS.USERNAME),
            record.get(USERS.IS_ADMIN),
            record.get(USERS.BANNED_AT),
            record.get(USERS.BAN_REASON),
            membershipRole == null ? null : membershipRole.getLiteral());
    }
}
