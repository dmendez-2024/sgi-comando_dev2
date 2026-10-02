package com.cajamarca.sgi.comando.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/** Reads the published CORE country, subdivision and company catalogs. */
@ApplicationScoped
public class CoreCatalogAdapter implements ExternalPorts.CorePort {
    @ConfigProperty(name = "sgi.core.catalog-base-url")
    String coreCatalogBaseUrl;

    @ConfigProperty(name = "sgi.core.country-id")
    String countryId;

    @ConfigProperty(name = "sgi.core.instance-country-code", defaultValue = "ECU-CM")
    String instanceCountryCode;

    @ConfigProperty(name = "sgi.interconnections.core-timeout-ms", defaultValue = "5000")
    int timeoutMs;

    @Inject ObjectMapper mapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    /**
     * The SGI tenant ID is local. This port accepts the CORE country ID and
     * returns CORE's selected Instance-Country record.
     */
    @Override
    public Map<String,Object> instanceCountry(UUID coreCountryId) {
        return mapper.convertValue(selectInstanceCountry(fetchInstanceCountries(coreCountryId)), Map.class);
    }

    public record CatalogResponse(UUID coreInstanceCountryId, JsonNode context, JsonNode territory, JsonNode companies) {}

    public CatalogResponse fetchCatalog() {
        UUID coreCountryId = parseUuid(countryId, "SGI_CORE_COUNTRY_ID");
        JsonNode instanceCountry = selectInstanceCountry(fetchInstanceCountries(coreCountryId));
        UUID coreInstanceCountryId = parseUuid(text(instanceCountry, "id"), "CORE instance-country id");

        ObjectNode context = mapper.createObjectNode();
        context.put("instanceCountryId", coreInstanceCountryId.toString());
        context.set("country", instanceCountry);

        JsonNode territoryResponse = get("/api/v1/catalog/subdivisions?countryId=" + enc(coreCountryId.toString()));
        if (!territoryResponse.isObject()) throw new IllegalStateException("CORE devolvió el catálogo territorial con un formato inesperado.");
        ObjectNode territory = ((ObjectNode) territoryResponse).deepCopy();
        territory.put("instanceCountryId", coreInstanceCountryId.toString());
        JsonNode geoJson = get("/api/v1/catalog/subdivisions/geojson?countryId=" + enc(coreCountryId.toString()), "application/geo+json");
        mergeGeometry(territory, geoJson);

        JsonNode companiesResponse = get("/api/v1/catalog/companies");
        JsonNode companyItems = companiesResponse;
        if (companiesResponse.isObject()) {
            JsonNode nested = first(companiesResponse, "companies", "items", "data");
            if (nested != null) companyItems = nested;
        }
        if (!companyItems.isArray()) throw new IllegalStateException("CORE devolvió el catálogo de compañías con un formato inesperado.");
        var selectedCompanies = mapper.createArrayNode();
        for (JsonNode company : companyItems) {
            String sourceInstanceCountryId = text(company, "instanceCountryId", "instance_country_id");
            if (sourceInstanceCountryId == null) throw new IllegalStateException("CORE devolvió una Compañía sin instanceCountryId.");
            if (coreInstanceCountryId.toString().equalsIgnoreCase(sourceInstanceCountryId)) selectedCompanies.add(company);
        }
        if (selectedCompanies.isEmpty()) throw new IllegalStateException("CORE no devolvió compañías para la Instancia-País " + instanceCountryCode + ".");
        ObjectNode companies = mapper.createObjectNode();
        companies.put("instanceCountryId", coreInstanceCountryId.toString());
        companies.set("companies", selectedCompanies);

        return new CatalogResponse(coreInstanceCountryId, context, territory, companies);
    }

    private JsonNode fetchInstanceCountries(UUID coreCountryId) {
        return get("/api/v1/catalog/instance-countries?countryId=" + enc(coreCountryId.toString()));
    }

    private JsonNode selectInstanceCountry(JsonNode response) {
        JsonNode rows = response;
        if (response.isObject()) {
            JsonNode nested = first(response, "instanceCountries", "items", "data");
            if (nested != null) rows = nested;
        }
        if (rows.isObject()) return rows;
        if (!rows.isArray() || rows.isEmpty()) {
            throw new IllegalStateException("CORE no encontró una Instancia-País para el país configurado.");
        }

        JsonNode match = null;
        for (JsonNode row : rows) {
            if (instanceCountryCode == null || instanceCountryCode.isBlank()
                || instanceCountryCode.equalsIgnoreCase(text(row, "code"))) {
                if (match != null) {
                    throw new IllegalStateException("CORE devolvió varias Instancias-País; configure SGI_CORE_INSTANCE_COUNTRY_CODE.");
                }
                match = row;
            }
        }
        if (match == null) {
            throw new IllegalStateException("CORE no devolvió la Instancia-País " + instanceCountryCode + ".");
        }
        return match;
    }

    private JsonNode get(String pathAndQuery) {
        return get(pathAndQuery, "application/json");
    }

    private JsonNode get(String pathAndQuery, String accept) {
        try {
            String base = coreCatalogBaseUrl == null ? "" : coreCatalogBaseUrl.trim().replaceAll("/+$", "");
            if (base.isEmpty()) throw new IllegalStateException("No está configurada la URL del catálogo CORE.");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + pathAndQuery))
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 250)))
                .header("Accept", accept)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = remoteMessage(response.body());
                String message = "CORE respondió HTTP " + response.statusCode() + " al consultar " + pathAndQuery.substring(0, pathAndQuery.indexOf('?'));
                if (detail != null) message += ": " + detail;
                throw new IllegalStateException(message);
            }
            JsonNode body = mapper.readTree(response.body());
            if (body == null || body.isNull()) throw new IllegalStateException("CORE devolvió una respuesta vacía para " + pathAndQuery);
            return body;
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo consultar el catálogo CORE " + pathAndQuery.substring(0, pathAndQuery.indexOf('?')) + ".", error);
        }
    }

    private String remoteMessage(String body) {
        try {
            JsonNode message = mapper.readTree(body).get("message");
            if (message == null || message.isNull() || !message.isValueNode()) return null;
            String detail = message.asText().trim();
            if (detail.isEmpty()) return null;
            return detail.length() > 500 ? detail.substring(0, 500) : detail;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void mergeGeometry(ObjectNode territory, JsonNode geoJson) {
        if (!"FeatureCollection".equals(text(geoJson, "type"))) {
            throw new IllegalStateException("CORE no devolvió las geometrías territoriales como GeoJSON FeatureCollection.");
        }
        String expectedVersion = text(territory, "datasetVersion", "territorialDatasetVersion");
        String geometryVersion = text(geoJson, "datasetVersion", "territorialDatasetVersion");
        if (expectedVersion != null && geometryVersion != null && !expectedVersion.equals(geometryVersion)) {
            throw new IllegalStateException("CORE devolvió provincias y geometrías de versiones territoriales distintas.");
        }
        JsonNode subdivisions = territory.get("subdivisions");
        JsonNode features = geoJson.get("features");
        if (subdivisions == null || !subdivisions.isArray() || features == null || !features.isArray()
            || subdivisions.size() != features.size()) {
            throw new IllegalStateException("CORE devolvió una cantidad distinta de provincias y geometrías.");
        }

        Map<String,JsonNode> geometryById = new HashMap<>();
        for (JsonNode feature : features) {
            String id = text(feature, "id");
            JsonNode geometry = feature.get("geometry");
            if (id == null || geometry == null || geometry.isNull() || geometryById.put(id, geometry) != null) {
                throw new IllegalStateException("CORE devolvió una geometría sin ID, vacía o duplicada.");
            }
        }
        for (JsonNode subdivision : subdivisions) {
            String id = text(subdivision, "id", "subdivisionId", "subdivision_id");
            JsonNode geometry = geometryById.get(id);
            if (id == null || geometry == null) {
                throw new IllegalStateException("CORE no devolvió la geometría de una provincia del catálogo.");
            }
            if (!(subdivision instanceof ObjectNode item)) {
                throw new IllegalStateException("CORE devolvió una provincia con formato inesperado.");
            }
            item.set("geometry", geometry);
        }
    }

    private static JsonNode first(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? null : value.asText().trim();
    }

    private static String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null || value.isContainerNode() ? null : value.asText().trim();
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value == null ? "" : value.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("El valor de " + field + " no es un UUID válido.", error);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
