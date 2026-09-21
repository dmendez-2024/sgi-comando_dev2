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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Scope rules for ASI, including Kaibil coordination-company permissions. */
@ApplicationScoped
public class AssignmentScopeService {
    private static final Set<String> COUNTRY_READ_ROLES=Set.of("PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL");
    private static final Set<String> KAIBIL_EDIT_ROLES=Set.of("PRESIDENTE","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL");
    private static final Set<String> STANDARD_EDIT_ROLES=Set.of("COORDINADOR_COMPANIA","ASISTENTE_COORDINACION");

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;

    public boolean countryWide(){return identity.getRoles().stream().anyMatch(COUNTRY_READ_ROLES::contains);}
    public List<UserOperationalScope> scopes(){return UserOperationalScope.list("instanceCountryId=?1 and username=?2",tenant.instanceCountryId(),identity.getPrincipal().getName());}

    public Set<UUID> allowedCompanyIds(){
        if(countryWide())return Company.<Company>list("instanceCountryId=?1",tenant.instanceCountryId()).stream().map(c->c.id).collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<UUID> out=new LinkedHashSet<>();
        for(UserOperationalScope s:scopes()){
            if(s.scopeId==null)continue;
            if("ZONE".equals(s.scopeType))Company.<Company>list("instanceCountryId=?1 and zoneId=?2",tenant.instanceCountryId(),s.scopeId).forEach(c->out.add(c.id));
            else if("REGION".equals(s.scopeType))CompanyRegion.<CompanyRegion>list("instanceCountryId=?1 and regionId=?2",tenant.instanceCountryId(),s.scopeId).forEach(l->out.add(l.companyId));
            else if("COMPANY".equals(s.scopeType))out.add(s.scopeId);
        }
        if(identity.getRoles().stream().anyMatch(KAIBIL_EDIT_ROLES::contains))Company.<Company>list("instanceCountryId=?1 and alwaysActive=true",tenant.instanceCountryId()).forEach(c->out.add(c.id));
        return out;
    }

    public void requireCompany(UUID companyId){if(companyId==null||!allowedCompanyIds().contains(companyId))throw new ForbiddenException("La Compañía está fuera del alcance territorial del usuario.");}

    public boolean canEditCompany(UUID companyId){
        Company c=Company.find("id=?1 and instanceCountryId=?2",companyId,tenant.instanceCountryId()).firstResult();if(c==null)return false;
        if(c.alwaysActive||"COORDINATION".equals(c.companyType))return identity.getRoles().stream().anyMatch(KAIBIL_EDIT_ROLES::contains);
        return identity.getRoles().stream().anyMatch(STANDARD_EDIT_ROLES::contains)&&scopes().stream().anyMatch(s->"COMPANY".equals(s.scopeType)&&Objects.equals(s.scopeId,companyId));
    }

    public void requireEditorForCompany(UUID companyId){if(!canEditCompany(companyId))throw new ForbiddenException("El usuario no puede modificar Asignaciones de esta Compañía.");}

    /** For Kaibil transfer decisions, zone/region roles are limited by the counterparty Company's territory. */
    public boolean canManageKaibilTransferAgainst(UUID counterpartyCompanyId){
        if(identity.getRoles().contains("PRESIDENTE")||identity.getRoles().contains("DIRECTOR_NACIONAL"))return true;
        Company c=Company.find("id=?1 and instanceCountryId=?2",counterpartyCompanyId,tenant.instanceCountryId()).firstResult();if(c==null)return false;
        if(identity.getRoles().contains("DIRECTOR_ZONAL"))return scopes().stream().anyMatch(s->"ZONE".equals(s.scopeType)&&Objects.equals(s.scopeId,c.zoneId));
        if(identity.getRoles().contains("JEFE_REGIONAL")){Set<UUID> regions=CompanyRegion.<CompanyRegion>list("instanceCountryId=?1 and companyId=?2",tenant.instanceCountryId(),c.id).stream().map(l->l.regionId).collect(Collectors.toSet());return scopes().stream().anyMatch(s->"REGION".equals(s.scopeType)&&regions.contains(s.scopeId));}
        return false;
    }
}
