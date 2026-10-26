package zpi.project_bird.backend.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;

public enum OrganizationRole {
    VIEWER(MembershipRole._1_VIEWER),
    ANNOTATOR(MembershipRole._2_ANNOTATOR),
    EDITOR(MembershipRole._3_EDITOR),
    OWNER(MembershipRole._4_OWNER);

    private final MembershipRole membershipRole;

    OrganizationRole(MembershipRole membershipRole) {
        this.membershipRole = membershipRole;
    }

    @JsonCreator
    public static OrganizationRole fromJson(String value) {
        for (OrganizationRole role : values()) {
            if (role.name().equals(value) || role.membershipRole.getLiteral().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown organization role: " + value);
    }

    @JsonValue
    public String toJson() {
        return membershipRole.getLiteral();
    }

    MembershipRole toMembershipRole() {
        return membershipRole;
    }
}
