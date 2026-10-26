package zpi.project_bird.backend.organization;

import zpi.project_bird.backend.jooq.public_.enums.MembershipRole;

public record Membership(Organization organization, MembershipRole role) {
}
