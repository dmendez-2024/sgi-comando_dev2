package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.common.TenantContext;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class OperationalScopeService {
    private static final Set<String> COUNTRY_ROLES = Set.of("PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL");
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;

    public boolean countryWide(){ return identity.getRoles().stream().anyMatch(COUNTRY_ROLES::contains); }
    public String username(){ return identity.getPrincipal().getName(); }
    public List<UserOperationalScope> scopes(){ return UserOperationalScope.list("instanceCountryId=?1 and username=?2",tenant.instanceCountryId(),username()); }

    public Set<UUID> visibleZoneIds(){
        if(countryWide()){List<TerritoryZone> zones=TerritoryZone.list("instanceCountryId=?1",tenant.instanceCountryId());return zones.stream().map(z->z.id).collect(Collectors.toSet());}
        Set<UUID> zones=new HashSet<>();
        for(UserOperationalScope s:scopes()){
            if("ZONE".equals(s.scopeType)&&s.scopeId!=null) zones.add(s.scopeId);
            if("REGION".equals(s.scopeType)&&s.scopeId!=null){TerritoryRegion r=TerritoryRegion.find("id=?1 and instanceCountryId=?2",s.scopeId,tenant.instanceCountryId()).firstResult();if(r!=null)zones.add(r.zoneId);}
            if("COMPANY".equals(s.scopeType)&&s.scopeId!=null){Company c=Company.find("id=?1 and instanceCountryId=?2",s.scopeId,tenant.instanceCountryId()).firstResult();if(c!=null&&c.regionId!=null){TerritoryRegion r=TerritoryRegion.find("id=?1 and instanceCountryId=?2",c.regionId,tenant.instanceCountryId()).firstResult();if(r!=null)zones.add(r.zoneId);}}
        }
        return zones;
    }

    public Set<UUID> visibleRegionIds(){
        if(countryWide()){List<TerritoryRegion> regions=TerritoryRegion.list("instanceCountryId=?1",tenant.instanceCountryId());return regions.stream().map(r->r.id).collect(Collectors.toSet());}
        Set<UUID> regions=new HashSet<>();
        for(UserOperationalScope s:scopes()){
            if("ZONE".equals(s.scopeType)&&s.scopeId!=null){List<TerritoryRegion> list=TerritoryRegion.list("instanceCountryId=?1 and zoneId=?2",tenant.instanceCountryId(),s.scopeId);list.forEach(r->regions.add(r.id));}
            if("REGION".equals(s.scopeType)&&s.scopeId!=null) regions.add(s.scopeId);
            if("COMPANY".equals(s.scopeType)&&s.scopeId!=null){Company c=Company.find("id=?1 and instanceCountryId=?2",s.scopeId,tenant.instanceCountryId()).firstResult();if(c!=null&&c.regionId!=null)regions.add(c.regionId);}
        }
        return regions;
    }

    public Set<UUID> allowedCompanyIds(){
        if(countryWide()){List<Company> companies=Company.list("instanceCountryId=?1",tenant.instanceCountryId());return companies.stream().map(c->c.id).collect(Collectors.toSet());}
        Set<UUID> out=new HashSet<>(); Set<UUID> regions=visibleRegionIds();
        if(!regions.isEmpty()){List<Company> companies=Company.list("instanceCountryId=?1 and regionId in ?2",tenant.instanceCountryId(),regions);companies.forEach(c->out.add(c.id));}
        for(UserOperationalScope s:scopes()) if("COMPANY".equals(s.scopeType)&&s.scopeId!=null) out.add(s.scopeId);
        return out;
    }

    public boolean canAccessCompany(UUID companyId){ return companyId!=null&&allowedCompanyIds().contains(companyId); }
    public void requireCompany(UUID companyId){ if(!canAccessCompany(companyId)) throw new ForbiddenException("La Compañía está fuera del alcance territorial del usuario."); }
}
