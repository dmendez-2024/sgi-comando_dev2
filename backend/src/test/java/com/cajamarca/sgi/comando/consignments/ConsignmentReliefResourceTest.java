package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.inventory.InventoryService;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsignmentReliefResourceTest {
    private final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),relief=UUID.randomUUID();
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void noScopeReturnsEmptyWithoutReadingTheDatabase(){
        assertTrue(resource(Set.of()).list(100).isEmpty());
        assertThrows(NotFoundException.class,()->resource(Set.of()).detail(relief));
    }

    @Test void listIsBoundedScopedAndDoesNotLoadInventoryOrFullPayload(){
        ConsignmentReliefResource resource=resource(Set.of(company));
        Map<String,Object> params=new HashMap<>();
        List<Object[]> data=new ArrayList<>();
        for(int i=0;i<100;i++)data.add(summaryRow());
        mockQuery(resource,data,params);
        ArrayNode result=resource.list(1000);
        assertEquals(100,result.size());
        assertEquals(100,params.get("limit"));
        assertEquals(tenant,params.get("tenant"));
        assertEquals(Set.of(company),params.get("companies"));
        assertEquals(1,params.get("queries"));
        assertFalse(result.get(0).has("context"));
        assertFalse(result.get(0).has("inventoryReport"));
        assertEquals("OPENING_WITHOUT_PREVIOUS_SHIFT",result.get(0).path("event").path("reliefMode").asText());
        assertEquals("Agente entrante",result.get(0).path("incomingEmployeeName").asText());
        assertTrue(result.toString().length()<100_000,"100 summaries must not contain megabytes of context");
        // inventory is not injected; any accidental eager inventory lookup would fail this test.
    }

    @Test void inaccessibleDetailDoesNotLoadInventory(){
        ConsignmentReliefResource resource=resource(Set.of(company));
        Map<String,Object> params=new HashMap<>();
        mockQuery(resource,List.of(),params);
        assertThrows(NotFoundException.class,()->resource.detail(relief));
        assertEquals(relief,params.get("id"));
        assertEquals(Set.of(company),params.get("companies"));
    }

    @Test void detailLoadsFullContextAndInventoryOnlyForTheRequestedRelief() throws Exception {
        ConsignmentReliefResource resource=resource(Set.of(company));
        Object[] detail=Arrays.copyOf(summaryRow(),18);
        detail[16]="{\"incomingEmployeeName\":\"Agente entrante\",\"notes\":\""+ "x".repeat(40_000)+"\"}";
        detail[17]="{\"executedAt\":\"2026-11-12T14:00:00Z\",\"unilateral\":false,\"reliefMode\":\"OPENING_WITHOUT_PREVIOUS_SHIFT\"}";
        Map<String,Object> params=new HashMap<>();
        mockQuery(resource,Collections.singletonList(detail),params);
        resource.inventory=new InventoryService(){
            @Override public String reportStatus(UUID id){assertEquals(relief,id);return "DELIVERED";}
            @Override public ArrayNode reportItems(UUID id){assertEquals(relief,id);ArrayNode items=ConsignmentReliefResourceTest.this.mapper.createArrayNode();items.addObject().put("description","Linterna");return items;}
        };
        var result=resource.detail(relief);
        assertEquals(relief,params.get("id"));
        assertEquals(1,params.get("limit"));
        assertEquals(40_000,result.path("context").path("notes").asText().length());
        assertEquals("DELIVERED",result.path("inventoryDeliveryStatus").asText());
        assertEquals("Linterna",result.path("inventoryReport").path("items").get(0).path("description").asText());
    }

    private ConsignmentReliefResource resource(Set<UUID> companies){
        ConsignmentReliefResource result=new ConsignmentReliefResource();
        result.mapper=mapper;
        result.scope=new OperationalScopeService(){@Override public Set<UUID> allowedCompanyIdsForCompanyRoles(){return companies;}};
        result.tenant=new TenantContext(){@Override public UUID instanceCountryId(){return tenant;}};
        return result;
    }
    private Object[] summaryRow(){
        return new Object[]{relief,"Acceso","Punto",UUID.randomUUID(),Instant.parse("2026-11-12T14:01:00Z"),
            "Agente","COMPLETE","Cliente","Guayaquil","GAL","Galvarino","Agente entrante",
            Instant.parse("2026-11-12T14:00:00Z"),false,"OPENING_WITHOUT_PREVIOUS_SHIFT",3L};
    }
    private void mockQuery(ConsignmentReliefResource resource,List<Object[]> rows,Map<String,Object> params){
        Query query=(Query)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Query.class},(proxy,method,args)->{
            if(method.getName().equals("setParameter")){params.put((String)args[0],args[1]);return proxy;}
            if(method.getName().equals("setMaxResults")){params.put("limit",args[0]);return proxy;}
            if(method.getName().equals("getResultList"))return rows;
            throw new UnsupportedOperationException(method.getName());
        });
        resource.em=(EntityManager)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{EntityManager.class},(proxy,method,args)->{
            if(method.getName().equals("createNativeQuery")){params.merge("queries",1,(a,b)->(int)a+(int)b);return query;}
            throw new UnsupportedOperationException(method.getName());
        });
    }
}
