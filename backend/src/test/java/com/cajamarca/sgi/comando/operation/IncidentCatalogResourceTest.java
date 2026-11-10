package com.cajamarca.sgi.comando.operation;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class IncidentCatalogResourceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Inject EntityManager em;
    UUID categoryId;
    UUID subcategoryId;
    UUID incidentTypeId;
    UUID severityId;
    String suffix;
    String categoryKey;
    String categoryName;
    String subcategoryName;
    String incidentTypeName;
    String severityKey;
    String severityName;

    @BeforeEach
    void createCatalog() {
        categoryId = UUID.randomUUID();
        subcategoryId = UUID.randomUUID();
        incidentTypeId = UUID.randomUUID();
        severityId = UUID.randomUUID();
        suffix = categoryId.toString().substring(0, 8);
        categoryKey = "TEST_CATEGORY_" + suffix;
        categoryName = "Categoría prueba " + suffix;
        subcategoryName = "Subcategoría prueba " + suffix;
        incidentTypeName = "Incidente prueba " + suffix;
        severityKey = "TEST_SEVERITY_" + suffix;
        severityName = "Criticidad prueba " + suffix;

        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                insert into incident_category_catalog
                  (id,instance_country_id,system_key,name,code,display_order,active)
                values (:id,:tenant,:key,:name,null,1,true)
                """).setParameter("id", categoryId).setParameter("tenant", TENANT)
                .setParameter("key", categoryKey).setParameter("name", categoryName).executeUpdate();
            em.createNativeQuery("""
                insert into incident_subcategory_catalog
                  (id,instance_country_id,category_id,name,code,display_order,active)
                values (:id,:tenant,:category,:name,null,1,true)
                """).setParameter("id", subcategoryId).setParameter("tenant", TENANT)
                .setParameter("category", categoryId).setParameter("name", subcategoryName).executeUpdate();
            em.createNativeQuery("""
                insert into incident_type_catalog
                  (id,instance_country_id,subcategory_id,name,code,display_order,active)
                values (:id,:tenant,:subcategory,:name,null,1,true)
                """).setParameter("id", incidentTypeId).setParameter("tenant", TENANT)
                .setParameter("subcategory", subcategoryId).setParameter("name", incidentTypeName).executeUpdate();
            em.createNativeQuery("""
                insert into incident_severity_catalog
                  (id,instance_country_id,system_key,name,code,display_order,active)
                values (:id,:tenant,:key,:name,null,1,true)
                """).setParameter("id", severityId).setParameter("tenant", TENANT)
                .setParameter("key", severityKey).setParameter("name", severityName).executeUpdate();
        });
    }

    @AfterEach
    void removeCatalog() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("delete from incident_notification where category_catalog_id=:category")
                .setParameter("category", categoryId).executeUpdate();
            em.createNativeQuery("delete from incident_type_catalog where id=:id").setParameter("id", incidentTypeId).executeUpdate();
            em.createNativeQuery("delete from incident_subcategory_catalog where id=:id").setParameter("id", subcategoryId).executeUpdate();
            em.createNativeQuery("delete from incident_severity_catalog where id=:id").setParameter("id", severityId).executeUpdate();
            em.createNativeQuery("delete from incident_category_catalog where id=:id").setParameter("id", categoryId).executeUpdate();
        });
    }

    @Test
    void catalogAndDraftPersistIdsAndCanonicalNames() {
        as("coord").get("/api/incidents/catalog").then().statusCode(200)
            .body("categories.find { it.id == '" + categoryId + "' }.name", is(categoryName))
            .body("categories.find { it.id == '" + categoryId + "' }.subcategories[0].name", is(subcategoryName))
            .body("categories.find { it.id == '" + categoryId + "' }.subcategories[0].incidentTypes[0].name", is(incidentTypeName))
            .body("severities.find { it.id == '" + severityId + "' }.name", is(severityName));

        Map<String,Object> body = new LinkedHashMap<>();
        body.put("status", "DRAFT");
        body.put("categoryCatalogId", categoryId);
        body.put("category", "FORGED_CATEGORY");
        body.put("categoryName", "Nombre alterado");
        body.put("subcategoryCatalogId", subcategoryId);
        body.put("subcategory", "Nombre alterado");
        body.put("incidentTypeCatalogId", incidentTypeId);
        body.put("incidentType", "Nombre alterado");
        body.put("severityCatalogId", severityId);
        body.put("severity", "FORGED_SEVERITY");
        body.put("severityName", "Nombre alterado");

        var saved = as("coord").contentType(ContentType.JSON).body(body).post("/api/incidents").then().statusCode(200)
            .body("categoryCatalogId", is(categoryId.toString())).body("category", is(categoryKey)).body("categoryName", is(categoryName))
            .body("subcategoryCatalogId", is(subcategoryId.toString())).body("subcategory", is(subcategoryName))
            .body("incidentTypeCatalogId", is(incidentTypeId.toString())).body("incidentType", is(incidentTypeName))
            .body("severityCatalogId", is(severityId.toString())).body("severity", is(severityKey)).body("severityName", is(severityName))
            .extract();

        UUID incidentId = UUID.fromString(saved.path("id"));
        Object[] stored = QuarkusTransaction.requiringNew().call(() -> (Object[]) em.createNativeQuery("""
            select category_catalog_id,subcategory_catalog_id,incident_type_catalog_id,severity_catalog_id,payload_json
              from incident_notification where id=:id
            """).setParameter("id", incidentId).getSingleResult());
        assertEquals(categoryId, stored[0]);
        assertEquals(subcategoryId, stored[1]);
        assertEquals(incidentTypeId, stored[2]);
        assertEquals(severityId, stored[3]);
        assertTrue(stored[4].toString().contains("\"categoryName\":\"" + categoryName + "\""));
        assertTrue(stored[4].toString().contains("\"severityName\":\"" + severityName + "\""));

        as("coord").contentType(ContentType.JSON).body(Map.of(
            "status", "DRAFT",
            "category", categoryKey,
            "subcategory", subcategoryName,
            "incidentType", incidentTypeName,
            "severity", severityKey
        )).post("/api/incidents").then().statusCode(200)
            .body("categoryCatalogId", is(categoryId.toString()))
            .body("subcategoryCatalogId", is(subcategoryId.toString()))
            .body("incidentTypeCatalogId", is(incidentTypeId.toString()))
            .body("severityCatalogId", is(severityId.toString()));
    }
}
