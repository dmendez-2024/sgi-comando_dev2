package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NotificationResourceTest {
    private final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID();

    @Test void userWithoutAssignedScopeGetsNoNotificationsAndDoesNotQueryReports(){
        NotificationResource resource=new NotificationResource();
        resource.scope=scope(Set.of());
        // No EntityManager: attempting to query would fail this test.
        assertEquals(new NotificationResource.Feed(0,List.of()),resource.list());
    }

    @Test void feedBindsTheAuthenticatedTenantAndOnlyAuthorizedCompanies(){
        NotificationResource resource=new NotificationResource();
        resource.scope=scope(Set.of(company));
        resource.tenant=new TenantContext(){@Override public UUID instanceCountryId(){return tenant;}};
        Map<String,Object> parameters=new HashMap<>();
        Object[] row={UUID.randomUUID(),"INCIDENT","Acceso sin control","CRITICAL","REPORTED","Galvarino","Cliente","Punto",
            new BigDecimal("1794420000"),75L};
        Query query=(Query)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Query.class},(proxy,method,args)->{
            if(method.getName().equals("setParameter")){parameters.put((String)args[0],args[1]);return proxy;}
            if(method.getName().equals("getResultList"))return Collections.singletonList(row);
            throw new UnsupportedOperationException(method.getName());
        });
        resource.em=(EntityManager)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{EntityManager.class},(proxy,method,args)->{
            if(method.getName().equals("createNativeQuery"))return query;
            throw new UnsupportedOperationException(method.getName());
        });
        NotificationResource.Feed feed=resource.list();
        assertEquals(tenant,parameters.get("tenant"));
        assertEquals(Set.of(company),parameters.get("companies"));
        assertEquals(75,feed.total(),"Counter includes cases beyond the displayed preview");
        assertEquals("CRITICAL",feed.items().getFirst().status());
        assertNotNull(feed.items().getFirst().occurredAt());
    }

    @Test void detailOutsideScopeIsNotReturnedEvenIfItsIdentifierIsKnown(){
        OperationalReportResource resource=new OperationalReportResource();
        resource.scope=scope(Set.of(company));
        UUID requested=UUID.randomUUID();
        resource.reports=new OperatorReportResource(){
            @Override ArrayNode rows(UUID employee,String type,Set<UUID> companies,UUID id){
                assertEquals(Set.of(company),companies);
                assertEquals(requested,id);
                return new ObjectMapper().createArrayNode();
            }
        };
        assertThrows(NotFoundException.class,()->resource.detail(requested));
    }

    private OperationalScopeService scope(Set<UUID> companies){
        return new OperationalScopeService(){@Override public Set<UUID> allowedCompanyIdsForCompanyRoles(){return companies;}};
    }
}
