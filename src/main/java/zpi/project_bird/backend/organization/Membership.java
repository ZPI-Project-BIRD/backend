package zpi.project_bird.backend.organization;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;

public record Membership(Organization organization, MembershipRole role) {
    public boolean canEdit() {
        return role == MembershipRole._4_OWNER || role == MembershipRole._3_EDITOR;
    }
    public boolean canOwn() {
        return role == MembershipRole._4_OWNER;
    }
}
