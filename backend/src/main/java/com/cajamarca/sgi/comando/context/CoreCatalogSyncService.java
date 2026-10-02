package com.cajamarca.sgi.comando.context;

import com.cajamarca.sgi.comando.companies.CoreCompanyCatalogSnapshot;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.companies.CompanyRegion;
import com.cajamarca.sgi.comando.companies.CompanyVersion;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.integration.CoreCatalogAdapter;
import com.cajamarca.sgi.comando.territory.CountrySubdivision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CoreCatalogSyncService {
    @Inject TenantContext tenant;
    @Inject EntityManager entityManager;
    @Inject ObjectMapper mapper;

    @Transactional
    public CoreInstanceCountrySnapshot synchronize(CoreCatalogAdapter.CatalogResponse response) {
        UUID tenantId = tenant.instanceCountryId();
        UUID coreInstanceCountryId = response.coreInstanceCountryId();
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(?1))")
            .setParameter(1, "sgi-core-catalog:" + tenantId)
            .getSingleResult();
        JsonNode context = payload(response.context());
        JsonNode territory = payload(response.territory());
        JsonNode companies = payload(response.companies());
        validateTenant(context, coreInstanceCountryId);
        validateTenant(territory, coreInstanceCountryId);
        validateTenant(companies, coreInstanceCountryId);
        JsonNode country = first(context, "country", "instanceCountry");
        if (country == null || !country.isObject()) country = context;
        validateTenant(country, coreInstanceCountryId);
        JsonNode structure = first(territory, "territorialStructure", "structure");
        if (structure != null && structure.isObject()) {
            validateTenant(structure, tenantId);
            territory = structure;
        }

        String countryCode = required(text(country, "countryCode", "country_code", "code"), "countryCode");
        String countryName = required(text(country, "countryName", "country_name", "name", "country"), "countryName");
        String subdivisionType = required(text(territory, "subdivisionType", "subdivision_type"), "subdivisionType");
        String singular = required(text(territory, "subdivisionSingular", "subdivision_singular"), "subdivisionSingular");
        String plural = required(text(territory, "subdivisionPlural", "subdivision_plural"), "subdivisionPlural");
        String datasetVersion = required(value(text(territory, "datasetVersion", "territorialDatasetVersion", "dataset_version"),
            text(context, "datasetVersion", "contextVersion", "version")), "territorialDatasetVersion");
        Instant now = Instant.now();

        CoreInstanceCountrySnapshot snapshot = CoreInstanceCountrySnapshot.find("instanceCountryId=?1", tenantId).firstResult();
        if (snapshot == null) {
            snapshot = new CoreInstanceCountrySnapshot();
            snapshot.instanceCountryId = tenantId;
        }
        snapshot.countryCode = countryCode;
        snapshot.countryName = countryName;
        snapshot.coreInstanceCountryId = coreInstanceCountryId;
        snapshot.locale = required(value(text(context, "locale", "languageTag"), text(country, "locale", "languageTag")), "locale");
        snapshot.timezone = required(value(text(context, "timezone", "timeZone"), text(country, "timezone", "timeZone")), "timezone");
        snapshot.currency = required(value(text(context, "currency", "currencyCode"), text(country, "currency", "currencyCode")), "currency");
        snapshot.subdivisionType = subdivisionType;
        snapshot.subdivisionSingular = singular;
        snapshot.subdivisionPlural = plural;
        snapshot.territorialDatasetVersion = datasetVersion;
        snapshot.syncedAt = now;
        if (snapshot.id == null) snapshot.persist();

        JsonNode subdivisions = collection(territory, "subdivisions", "provinces", "states", "items");
        if (subdivisions == null || !subdivisions.isArray()) throw new IllegalStateException("CORE no devolvió el catálogo de subdivisiones como lista.");
        if (subdivisions.isEmpty()) throw new IllegalStateException("CORE devolvió vacío el catálogo territorial de este país.");
        Set<UUID> seenSubdivisionIds = new HashSet<>();
        for (JsonNode item : subdivisions) {
            validateTenant(item, coreInstanceCountryId);
            seenSubdivisionIds.add(syncSubdivision(tenantId, item, datasetVersion));
        }
        CountrySubdivision.update(
            "status=?1, coreDatasetVersion=?2, updatedAt=?3 where instanceCountryId=?4 and coreSubdivisionId is not null and coreSubdivisionId not in ?5",
            "INACTIVE", datasetVersion, now, tenantId, seenSubdivisionIds
        );

        JsonNode companyItems = companies.isArray() ? companies : collection(companies, "companies", "items", "catalog");
        if (companyItems == null || !companyItems.isArray()) throw new IllegalStateException("CORE no devolvió el catálogo de compañías como lista.");
        Set<UUID> seenCompanyIds = new HashSet<>();
        for (JsonNode item : companyItems) {
            validateTenant(item, coreInstanceCountryId);
            seenCompanyIds.add(syncCompany(tenantId, item, datasetVersion, now));
        }
        if (seenCompanyIds.isEmpty()) {
            CoreCompanyCatalogSnapshot.update(
                "sourceStatus='INACTIVE', syncedAt=?1, updatedAt=?1 where instanceCountryId=?2", now, tenantId
            );
        } else {
            CoreCompanyCatalogSnapshot.update(
                "sourceStatus='INACTIVE', syncedAt=?1, updatedAt=?1 where instanceCountryId=?2 and coreCompanyId not in ?3",
                now, tenantId, seenCompanyIds
            );
        }
        return snapshot;
    }

    public CoreInstanceCountrySnapshot current() {
        return CoreInstanceCountrySnapshot.find("instanceCountryId=?1", tenant.instanceCountryId()).firstResult();
    }

    private void rebindCompanyId(UUID tenantId, CoreCompanyCatalogSnapshot snapshot, UUID newCoreId, String code, String sourceVersion, Instant syncedAt) {
        UUID oldCoreId = snapshot.coreCompanyId;
        Company oldLink = Company.find("instanceCountryId=?1 and coreCatalogId=?2", tenantId, oldCoreId).firstResult();
        Company newLink = Company.find("instanceCountryId=?1 and coreCatalogId=?2", tenantId, newCoreId).firstResult();
        if (oldLink != null && !oldLink.code.equals(snapshot.code)) {
            throw new IllegalStateException("La Compañía SGI vinculada al ID CORE anterior no coincide con su catálogo local.");
        }
        if (newLink != null && oldLink != null && !newLink.id.equals(oldLink.id)) {
            throw new IllegalStateException("Dos Compañías SGI quedarían vinculadas al ID CORE " + newCoreId + ".");
        }
        if (newLink != null && !newLink.code.equals(code)) {
            throw new IllegalStateException("El ID CORE " + newCoreId + " ya está vinculado a otra Compañía SGI.");
        }
        if (oldLink != null) {
            oldLink.coreCatalogId = newCoreId;
            oldLink.sourceVersion = sourceVersion;
            oldLink.updatedAt = syncedAt;
        }
        snapshot.coreCompanyId = newCoreId;
    }

    private void synchronizeActivatedCompany(UUID tenantId, CoreCompanyCatalogSnapshot source, Instant syncedAt, UUID previousCoreId) {
        Company operational = Company.find("instanceCountryId=?1 and coreCatalogId=?2", tenantId, source.coreCompanyId).firstResult();
        if (operational == null) return;
        Company duplicateCode = Company.find("instanceCountryId=?1 and code=?2", tenantId, source.code).firstResult();
        if (duplicateCode != null && !duplicateCode.id.equals(operational.id)) {
            throw new IllegalStateException("El código CORE " + source.code + " ya está asignado a otra Compañía operacional SGI.");
        }

        boolean alwaysActive = "COORDINATION".equals(source.companyType);
        boolean changed = previousCoreId != null
            || !Objects.equals(operational.code, source.code)
            || !Objects.equals(operational.name, source.name)
            || !Objects.equals(operational.historicalReview, source.historicalReview)
            || !Objects.equals(operational.logoDataUrl, source.logoDataUrl)
            || !Objects.equals(operational.sourceSystem, "CORE")
            || !Objects.equals(operational.sourceVersion, source.sourceVersion)
            || !Objects.equals(operational.companyType, source.companyType)
            || operational.alwaysActive != alwaysActive;
        if (!changed) return;

        operational.code = source.code;
        operational.name = source.name;
        operational.historicalReview = source.historicalReview;
        operational.logoDataUrl = source.logoDataUrl;
        operational.sourceSystem = "CORE";
        operational.sourceVersion = source.sourceVersion;
        operational.companyType = source.companyType;
        operational.alwaysActive = alwaysActive;
        operational.versionNumber = Math.max(operational.versionNumber, 1) + 1;
        persistCoreCompanyVersion(operational, syncedAt, previousCoreId);
    }

    private void persistCoreCompanyVersion(Company company, Instant effectiveAt, UUID previousCoreId) {
        try {
            ObjectNode snapshot = mapper.createObjectNode();
            snapshot.put("code", company.code);
            snapshot.put("name", company.name);
            snapshot.put("status", company.status);
            if (company.zoneId == null) snapshot.putNull("zoneId"); else snapshot.put("zoneId", company.zoneId.toString());
            ArrayNode regions = snapshot.putArray("regionIds");
            for (CompanyRegion link : CompanyRegion.<CompanyRegion>list("instanceCountryId=?1 and companyId=?2", company.instanceCountryId, company.id)) regions.add(link.regionId.toString());
            if (company.responsibleEmployeeId == null) snapshot.putNull("responsibleEmployeeId"); else snapshot.put("responsibleEmployeeId", company.responsibleEmployeeId.toString());
            if (company.historicalReview == null) snapshot.putNull("historicalReview"); else snapshot.put("historicalReview", company.historicalReview);
            if (company.logoDataUrl == null) snapshot.putNull("logoDataUrl"); else snapshot.put("logoDataUrl", company.logoDataUrl);
            snapshot.put("coreCatalogId", company.coreCatalogId.toString());
            if (previousCoreId == null) snapshot.putNull("previousCoreCatalogId"); else snapshot.put("previousCoreCatalogId", previousCoreId.toString());
            snapshot.put("sourceSystem", company.sourceSystem);
            snapshot.put("sourceVersion", company.sourceVersion);
            snapshot.put("companyType", company.companyType);
            snapshot.put("alwaysActive", company.alwaysActive);
            snapshot.put("versionNumber", company.versionNumber);

            CompanyVersion version = new CompanyVersion();
            version.instanceCountryId = company.instanceCountryId;
            version.companyId = company.id;
            version.versionNumber = company.versionNumber;
            version.changeType = "CORE_MASTER_SYNC";
            version.changeReason = "Sincronización de identidad desde CORE";
            version.actorUsername = "CORE_SYNC";
            version.effectiveAt = effectiveAt;
            version.snapshotJson = mapper.writeValueAsString(snapshot);
            version.persist();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo registrar la actualización CORE de la Compañía operacional.", error);
        }
    }

    private UUID syncSubdivision(UUID tenantId, JsonNode item, String datasetVersion) {
        UUID coreId = uuid(text(item, "id", "subdivisionId", "subdivision_id", "coreSubdivisionId", "core_subdivision_id", "coreId"), "subdivisionId");
        String code = required(text(item, "officialCode", "official_code", "code", "subdivisionCode", "subdivision_code"), "subdivision.code");
        String name = required(text(item, "name", "subdivisionName", "subdivision_name"), "subdivision.name");
        CountrySubdivision byCoreId = CountrySubdivision.find("instanceCountryId=?1 and coreSubdivisionId=?2", tenantId, coreId).firstResult();
        CountrySubdivision byCode = CountrySubdivision.find("instanceCountryId=?1 and code=?2", tenantId, code).firstResult();
        CountrySubdivision byName = null;
        if (byCoreId == null && byCode == null) {
            var nameMatches = CountrySubdivision.<CountrySubdivision>list("instanceCountryId=?1", tenantId).stream()
                .filter(candidate -> normalizedName(candidate.name).equals(normalizedName(name))).toList();
            if (nameMatches.size() > 1) throw new IllegalStateException("CORE devolvió el nombre territorial ambiguo " + name + ".");
            if (!nameMatches.isEmpty()) byName = nameMatches.get(0);
        }
        if (byCoreId != null && byCode != null && !byCoreId.id.equals(byCode.id)) throw new IllegalStateException("CORE devolvió una identidad territorial ambigua para el código " + code + ".");
        CountrySubdivision subdivision = byCoreId != null ? byCoreId : byCode != null ? byCode : byName;
        if (subdivision == null) {
            subdivision = new CountrySubdivision();
            subdivision.instanceCountryId = tenantId;
            subdivision.code = code;
            subdivision.name = name;
            subdivision.status = "ACTIVE";
        }
        subdivision.coreSubdivisionId = coreId;
        subdivision.code = code;
        subdivision.name = name;
        subdivision.status = normalizeStatus(value(text(item, "status", "sourceStatus", "source_status"), "ACTIVE"));
        subdivision.coreDatasetVersion = datasetVersion;
        JsonNode geometry = first(item, "geometry", "geometry_json", "polygon");
        if (geometry != null && !geometry.isNull()) {
            subdivision.geometryJson = geometry.isTextual() ? geometry.asText() : geometry.toString();
        }
        if (subdivision.id == null) subdivision.persist();
        return coreId;
    }

    private UUID syncCompany(UUID tenantId, JsonNode item, String datasetVersion, Instant syncedAt) {
        UUID coreId = uuid(text(item, "coreCompanyId", "core_company_id", "companyId", "company_id", "id", "coreId"), "company.coreCompanyId");
        String code = required(text(item, "code", "companyCode", "company_code", "officialCode", "official_code"), "company.code");
        String name = required(text(item, "name", "companyName", "company_name"), "company.name");
        String sourceVersion = value(text(item, "sourceVersion", "source_version", "version"), datasetVersion);
        CoreCompanyCatalogSnapshot byCoreId = CoreCompanyCatalogSnapshot.find("instanceCountryId=?1 and coreCompanyId=?2", tenantId, coreId).firstResult();
        CoreCompanyCatalogSnapshot byCode = CoreCompanyCatalogSnapshot.find("instanceCountryId=?1 and code=?2", tenantId, code).firstResult();
        CoreCompanyCatalogSnapshot byName = null;
        if (byCoreId == null && byCode == null) {
            var nameMatches = CoreCompanyCatalogSnapshot.<CoreCompanyCatalogSnapshot>list("instanceCountryId=?1", tenantId).stream()
                .filter(candidate -> normalizedName(candidate.name).equals(normalizedName(name))).toList();
            if (nameMatches.size() > 1) throw new IllegalStateException("CORE devolvió el nombre de compañía ambiguo " + name + ".");
            if (!nameMatches.isEmpty()) byName = nameMatches.get(0);
        }
        if (byCoreId != null && byCode != null && !byCoreId.id.equals(byCode.id)) throw new IllegalStateException("CORE devolvió una identidad ambigua para el código de compañía " + code + ".");
        if (byCoreId != null && byName != null && !byCoreId.id.equals(byName.id)) throw new IllegalStateException("CORE devolvió una identidad ambigua para el nombre de compañía " + name + ".");
        if (byCode != null && byName != null && !byCode.id.equals(byName.id)) throw new IllegalStateException("CORE devolvió una identidad ambigua para el código de compañía " + code + ".");
        CoreCompanyCatalogSnapshot company = byCoreId != null ? byCoreId : byCode != null ? byCode : byName;
        UUID previousCoreId = company != null && !company.coreCompanyId.equals(coreId) ? company.coreCompanyId : null;
        if (previousCoreId != null) rebindCompanyId(tenantId, company, coreId, code, sourceVersion, syncedAt);
        if (company == null) {
            company = new CoreCompanyCatalogSnapshot();
            company.instanceCountryId = tenantId;
            company.coreCompanyId = coreId;
            company.code = code;
            company.name = name;
            company.companyType = "SECURITY";
            company.sourceVersion = datasetVersion;
            company.sourceStatus = "ACTIVE";
            company.syncedAt = syncedAt;
        }
        company.coreCompanyId = coreId;
        company.code = code;
        company.name = name;
        company.historicalReview = text(item, "historicalReview", "historical_review", "description", "review");
        JsonNode logo = first(item, "logoDataUrl", "logo_data_url", "logoUrl", "logo_url", "logo");
        company.logoDataUrl = logo == null || logo.isNull() ? null : (logo.isTextual() ? logo.asText() : logo.toString());
        company.companyType = required(text(item, "companyType", "company_type", "type"), "company.companyType").toUpperCase(Locale.ROOT);
        company.sourceVersion = sourceVersion;
        company.sourceStatus = normalizeStatus(required(text(item, "status", "sourceStatus", "source_status"), "company.status"));
        company.syncedAt = syncedAt;
        if (company.id == null) company.persist();
        synchronizeActivatedCompany(tenantId, company, syncedAt, previousCoreId);
        return coreId;
    }

    private JsonNode payload(JsonNode root) {
        JsonNode data = first(root, "data", "result");
        return data != null && (data.isObject() || data.isArray()) ? data : root;
    }

    private void validateTenant(JsonNode node, UUID expected) {
        String supplied = text(node, "instanceCountryId", "instance_country_id");
        if (supplied != null && !expected.toString().equalsIgnoreCase(supplied)) {
            throw new IllegalStateException("CORE respondió datos de otra Instancia–País.");
        }
    }

    private JsonNode collection(JsonNode root, String... names) {
        JsonNode node = first(root, names);
        if (node == null || node.isArray()) return node;
        JsonNode nested = first(node, "items", "subdivisions", "companies");
        return nested == null ? node : nested;
    }

    private JsonNode first(JsonNode node, String... names) {
        if (node == null || node.isNull()) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) return value;
        }
        return null;
    }

    private String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        if (value == null || value.isContainerNode()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("CORE omitió el campo obligatorio " + field + ".");
        return value;
    }

    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private static String normalizedName(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private static UUID uuid(String value, String field) {
        try { return UUID.fromString(required(value, field)); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("CORE devolvió un " + field + " que no es UUID.", e); }
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return "ACTIVE";
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVA", "ACTIVO", "VIGENTE", "ENABLED" -> "ACTIVE";
            case "INACTIVA", "INACTIVO", "DISABLED", "RETIRED" -> "INACTIVE";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

}
