package zpi.project_bird.backend.organization;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
