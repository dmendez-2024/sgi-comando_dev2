package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.companies.CompanyRegion;
import com.cajamarca.sgi.comando.territory.UserOperationalScope;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alcance territorial propio de ASI.
 * Consume COM v1.0 FROZEN (Compañía mono-zona / multi-región) sin modificar COM ni TER.
 */
@ApplicationScoped
public class AssignmentScopeService {
    private static final Set<String> COUNTRY_ROLES = Set.of(
        "PRESIDENTE", "DIRECTOR_OPERACIONES_LATAM", "DIRECTOR_OPERACIONES_NACIONAL", "DIRECTOR_NACIONAL"
    );

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;

    public boolean countryWide() {
        return identity.getRoles().stream().anyMatch(COUNTRY_ROLES::contains);
    }

    public List<UserOperationalScope> scopes() {
        return UserOperationalScope.list(
            "instanceCountryId=?1 and username=?2",
            tenant.instanceCountryId(), identity.getPrincipal().getName()
        );
    }

    public Set<UUID> allowedCompanyIds() {
        if (countryWide()) {
            List<Company> all = Company.list("instanceCountryId=?1", tenant.instanceCountryId());
            return all.stream().map(c -> c.id).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        for (UserOperationalScope s : scopes()) {
            if (s.scopeId == null) continue;
            if ("ZONE".equals(s.scopeType)) {
                List<Company> companies = Company.list(
                    "instanceCountryId=?1 and zoneId=?2",
                    tenant.instanceCountryId(), s.scopeId
                );
                companies.forEach(c -> out.add(c.id));
            } else if ("REGION".equals(s.scopeType)) {
                List<CompanyRegion> links = CompanyRegion.list(
                    "instanceCountryId=?1 and regionId=?2",
                    tenant.instanceCountryId(), s.scopeId
                );
                links.forEach(link -> out.add(link.companyId));
            } else if ("COMPANY".equals(s.scopeType)) {
                out.add(s.scopeId);
            }
        }
        return out;
    }

    public void requireCompany(UUID companyId) {
        if (companyId == null || !allowedCompanyIds().contains(companyId)) {
            throw new ForbiddenException("La Compañía está fuera del alcance territorial del usuario.");
        }
    }
}
