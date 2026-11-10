package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class IncidentCatalogService {
    @Inject EntityManager em;
    @Inject TenantContext tenant;

    record Category(UUID id, String systemKey, String name) {}
    record Subcategory(UUID id, UUID categoryId, String name) {}
    record IncidentType(UUID id, UUID subcategoryId, String name) {}
    record Severity(UUID id, String systemKey, String name) {}
    record Selection(Category category, Subcategory subcategory, IncidentType incidentType, Severity severity) {}

    public ObjectNode catalog() {
        ObjectNode result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        ArrayNode categories = result.putArray("categories");
        Map<UUID,ObjectNode> categoriesById = new LinkedHashMap<>();
        Map<UUID,ObjectNode> subcategoriesById = new LinkedHashMap<>();

        for (Object[] row : rows("""
            select id,system_key,name
              from incident_category_catalog
             where instance_country_id=:tenant and active
             order by display_order,name
            """)) {
            UUID id = uuid(row[0]);
            ObjectNode category = categories.addObject()
                .put("id", id.toString())
                .put("systemKey", string(row[1]))
                .put("name", string(row[2]));
            category.putArray("subcategories");
            categoriesById.put(id, category);
        }

        for (Object[] row : rows("""
            select id,category_id,name
              from incident_subcategory_catalog
             where instance_country_id=:tenant and active
             order by category_id,display_order,name
            """)) {
            UUID id = uuid(row[0]), categoryId = uuid(row[1]);
            ObjectNode category = categoriesById.get(categoryId);
            if (category == null) continue;
            ObjectNode subcategory = category.withArray("subcategories").addObject()
                .put("id", id.toString())
                .put("name", string(row[2]));
            subcategory.putArray("incidentTypes");
            subcategoriesById.put(id, subcategory);
        }

        for (Object[] row : rows("""
            select id,subcategory_id,name
              from incident_type_catalog
             where instance_country_id=:tenant and active
             order by subcategory_id,display_order,name
            """)) {
            ObjectNode subcategory = subcategoriesById.get(uuid(row[1]));
            if (subcategory == null) continue;
            subcategory.withArray("incidentTypes").addObject()
                .put("id", uuid(row[0]).toString())
                .put("name", string(row[2]));
        }

        ArrayNode severities = result.putArray("severities");
        for (Object[] row : rows("""
            select id,system_key,name
              from incident_severity_catalog
             where instance_country_id=:tenant and active
             order by display_order,name
            """)) {
            severities.addObject()
                .put("id", uuid(row[0]).toString())
                .put("systemKey", string(row[1]))
                .put("name", string(row[2]));
        }
        return result;
    }

    Selection canonicalize(ObjectNode body, boolean requireComplete) {
        Category category = category(body);
        Subcategory subcategory = subcategory(body, category);
        IncidentType incidentType = incidentType(body, subcategory);
        Severity severity = severity(body);

        if (requireComplete && (category == null || subcategory == null || incidentType == null || severity == null))
            throw new BadRequestException("Seleccione categoría, subcategoría, incidente y criticidad antes de finalizar");

        put(body, "categoryCatalogId", category == null ? null : category.id());
        body.put("category", category == null ? "" : category.systemKey());
        body.put("categoryName", category == null ? "" : category.name());
        put(body, "subcategoryCatalogId", subcategory == null ? null : subcategory.id());
        body.put("subcategory", subcategory == null ? "" : subcategory.name());
        put(body, "incidentTypeCatalogId", incidentType == null ? null : incidentType.id());
        body.put("incidentType", incidentType == null ? "" : incidentType.name());
        put(body, "severityCatalogId", severity == null ? null : severity.id());
        body.put("severity", severity == null ? "" : severity.systemKey());
        body.put("severityName", severity == null ? "" : severity.name());
        return new Selection(category, subcategory, incidentType, severity);
    }

    private Category category(ObjectNode body) {
        UUID id = optionalUuid(body, "categoryCatalogId");
        String key = text(body, "category");
        if (id == null && key.isEmpty()) return null;
        List<Object[]> rows = id != null
            ? rows("select id,system_key,name from incident_category_catalog where instance_country_id=:tenant and active and id=:value", id)
            : rows("select id,system_key,name from incident_category_catalog where instance_country_id=:tenant and active and system_key=:value", key);
        if (rows.size() != 1) throw new BadRequestException("La categoría seleccionada no existe o está inactiva");
        Object[] row = rows.get(0);
        return new Category(uuid(row[0]), string(row[1]), string(row[2]));
    }

    private Subcategory subcategory(ObjectNode body, Category category) {
        UUID id = optionalUuid(body, "subcategoryCatalogId");
        String name = text(body, "subcategory");
        if (id == null && name.isEmpty()) return null;
        if (category == null) throw new BadRequestException("Seleccione la categoría antes de la subcategoría");
        List<Object[]> rows = id != null
            ? rows("select id,category_id,name from incident_subcategory_catalog where instance_country_id=:tenant and active and id=:value", id)
            : rows("select id,category_id,name from incident_subcategory_catalog where instance_country_id=:tenant and active and category_id=:parent and name=:value", category.id(), name);
        if (rows.size() != 1) throw new BadRequestException("La subcategoría seleccionada no existe o está inactiva");
        Object[] row = rows.get(0);
        Subcategory selected = new Subcategory(uuid(row[0]), uuid(row[1]), string(row[2]));
        if (!category.id().equals(selected.categoryId()))
            throw new BadRequestException("La subcategoría no pertenece a la categoría seleccionada");
        return selected;
    }

    private IncidentType incidentType(ObjectNode body, Subcategory subcategory) {
        UUID id = optionalUuid(body, "incidentTypeCatalogId");
        String name = text(body, "incidentType");
        if (id == null && name.isEmpty()) return null;
        if (subcategory == null) throw new BadRequestException("Seleccione la subcategoría antes del incidente");
        List<Object[]> rows = id != null
            ? rows("select id,subcategory_id,name from incident_type_catalog where instance_country_id=:tenant and active and id=:value", id)
            : rows("select id,subcategory_id,name from incident_type_catalog where instance_country_id=:tenant and active and subcategory_id=:parent and name=:value", subcategory.id(), name);
        if (rows.size() != 1) throw new BadRequestException("El incidente seleccionado no existe o está inactivo");
        Object[] row = rows.get(0);
        IncidentType selected = new IncidentType(uuid(row[0]), uuid(row[1]), string(row[2]));
        if (!subcategory.id().equals(selected.subcategoryId()))
            throw new BadRequestException("El incidente no pertenece a la subcategoría seleccionada");
        return selected;
    }

    private Severity severity(ObjectNode body) {
        UUID id = optionalUuid(body, "severityCatalogId");
        String key = text(body, "severity");
        if (id == null && key.isEmpty()) return null;
        List<Object[]> rows = id != null
            ? rows("select id,system_key,name from incident_severity_catalog where instance_country_id=:tenant and active and id=:value", id)
            : rows("select id,system_key,name from incident_severity_catalog where instance_country_id=:tenant and active and system_key=:value", key);
        if (rows.size() != 1) throw new BadRequestException("La criticidad seleccionada no existe o está inactiva");
        Object[] row = rows.get(0);
        return new Severity(uuid(row[0]), string(row[1]), string(row[2]));
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql) {
        return em.createNativeQuery(sql).setParameter("tenant", tenant.instanceCountryId()).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Object value) {
        return em.createNativeQuery(sql).setParameter("tenant", tenant.instanceCountryId())
            .setParameter("value", value).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Object parent, Object value) {
        return em.createNativeQuery(sql).setParameter("tenant", tenant.instanceCountryId())
            .setParameter("parent", parent).setParameter("value", value).getResultList();
    }

    private static void put(ObjectNode body, String field, UUID value) {
        body.put(field, value == null ? "" : value.toString());
    }

    private static UUID optionalUuid(ObjectNode body, String field) {
        String value = text(body, field);
        if (value.isEmpty()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw new BadRequestException(field + " inválido"); }
    }

    private static String text(ObjectNode body, String field) {
        return body.path(field).asText("").trim();
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
