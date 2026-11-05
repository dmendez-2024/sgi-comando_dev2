package com.cajamarca.sgi.comando.postconfig;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.operations.*;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the save transaction against PostgreSQL using independent fixtures. */
@QuarkusTest
@TestProfile(PostAliasPersistenceTest.Profile.class)
class PostAliasPersistenceTest {
    public static class Profile implements QuarkusTestProfile {
        @Override public Map<String,String> getConfigOverrides() {
            return Map.of("quarkus.scheduler.enabled","false","sgi.smc.enabled","false",
                "sgi.ident.publish-roles.enabled","false",
                "sgi.interconnections.core-resolver-url","http://127.0.0.1:1",
                "sgi.core.catalog-base-url","http://127.0.0.1:1");
        }
    }

    @Inject EntityManager em;
    @Inject TenantContext tenant;
    UUID companyId, clientId, serviceId, pointId, postId;
    PostConfigurationResource resource;

    @BeforeEach void setup() {
        QuarkusTransaction.requiringNew().run(()->{
            UUID country=tenant.instanceCountryId();
            String code="ALIAS-"+UUID.randomUUID().toString().substring(0,8);
            Company company=new Company(); company.instanceCountryId=country; company.code=code;
            company.name=code; company.status="ACTIVE"; company.sourceSystem="SIC_COM";
            company.companyType="OPERATIONAL"; company.versionNumber=1; company.persist(); companyId=company.id;
            ClientEntity client=new ClientEntity(); client.instanceCountryId=country; client.code=code;
            client.name=code; client.commercialStatus="ACTIVE"; client.sourceSystem="SIC_COM";
            client.persist(); clientId=client.id;
            ServiceEntity service=new ServiceEntity(); service.instanceCountryId=country; service.code=code;
            service.name=code; service.clientName=code; service.clientId=client.id;
            service.commercialStatus="ACTIVE"; service.configStatus="TO_CONFIGURE"; service.sourceSystem="SIC_COM";
            service.persist(); serviceId=service.id;
            PointEntity point=new PointEntity(); point.instanceCountryId=country; point.code=code;
            point.name=code; point.clientName=code; point.serviceId=service.id; point.companyId=company.id;
            point.status="ACTIVE"; point.operationalAssignmentStatus="ASSIGNED"; point.province="Guayas"; point.city="Guayaquil";
            point.persist(); pointId=point.id;
            PostEntity post=new PostEntity(); post.instanceCountryId=country; post.code=code; post.name="Guardianía";
            post.pointId=point.id; post.format="12H"; post.fhe=BigDecimal.valueOf(44); post.tier="I";
            post.commercialStatus="ACTIVE"; post.configStatus="TO_CONFIGURE"; post.persist(); postId=post.id;
        });
        resource=new PostConfigurationResource(); resource.tenant=tenant; resource.entityManager=em;
        // Authorization is unchanged; constrain direct test calls to this fixture's company.
        resource.scope=new OperationalScopeService(){@Override public void requireCompany(UUID id){assertEquals(companyId,id);}};
        resource.identity=(SecurityIdentity)Proxy.newProxyInstance(SecurityIdentity.class.getClassLoader(),
            new Class<?>[]{SecurityIdentity.class},(proxy,method,args)->{
                if(method.getName().equals("getPrincipal"))return (Principal)()->"alias-test";
                throw new UnsupportedOperationException(method.getName());
            });
    }

    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(()->{
            for (var entry:List.of(Map.entry("post_operational_config","post_id"),Map.entry("post","id"),
                    Map.entry("point","id"),Map.entry("service","id"),Map.entry("client","id"),Map.entry("company","id"))) {
                UUID id=switch(entry.getKey()){case "point"->pointId;case "service"->serviceId;
                    case "client"->clientId;case "company"->companyId;default->postId;};
                if(id!=null)em.createNativeQuery("delete from "+entry.getKey()+" where "+entry.getValue()+"=:id")
                    .setParameter("id",id).executeUpdate();
            }
        });
    }

    private PostConfigurationResource.SaveRequest request(String alias,String status,Integer radius) {
        return new PostConfigurationResource.SaveRequest("VIG","Control del puesto",alias,null,"ACCESO","Acceso",
            null,null,null,new PostConfigurationResource.SkillSet(2,2,2,2,2,2,2,2),null,status,
            false,null,null,null,radius);
    }

    @Test void aliasRenamesPostInDraftAndConfiguredSaves() {
        for(String status:List.of("DRAFT","CONFIGURED"))QuarkusTransaction.requiringNew().run(()->{
            String name="Acceso principal "+status;
            var saved=resource.save(postId,request("  "+name+"  ",status,null));
            assertEquals(name,saved.alias()); assertEquals(name,saved.postName());
            em.flush(); em.clear();
            PostEntity post=PostEntity.findById(postId);
            PostOperationalConfig config=PostOperationalConfig.find("postId",postId).firstResult();
            assertEquals(name,post.name); assertEquals(name,config.alias);
            assertEquals(saved.postCode(),post.code);
        });
    }

    @Test void emptyAliasDoesNotErasePostName() {
        for(String alias:Arrays.asList(null,"","   "))QuarkusTransaction.requiringNew().run(()->{
            var saved=resource.save(postId,request(alias,"DRAFT",null));
            assertNull(saved.alias()); assertEquals("Guardianía",saved.postName());
            em.flush(); em.clear();
            PostEntity post=PostEntity.findById(postId); assertEquals("Guardianía",post.name);
        });
    }

    @Test void failedSaveDoesNotPersistAliasOrRename() {
        assertThrows(BadRequestException.class,()->QuarkusTransaction.requiringNew().run(()->
            resource.save(postId,request("No debe persistir","DRAFT",1))));
        QuarkusTransaction.requiringNew().run(()->{
            PostEntity post=PostEntity.findById(postId); assertEquals("Guardianía",post.name);
            assertEquals(0,PostOperationalConfig.count("postId",postId));
        });
    }
}
