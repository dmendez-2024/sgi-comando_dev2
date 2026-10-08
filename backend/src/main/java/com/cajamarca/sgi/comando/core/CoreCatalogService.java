package com.cajamarca.sgi.comando.core;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.companies.CoreCompanyCatalogSnapshot;
import com.cajamarca.sgi.comando.territory.CountrySubdivision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptador provisional del catálogo CORE. Mientras IDENT no esté disponible,
 * el código de país se toma de configuración y se resuelve contra
 * /catalog/countries. Cuando IDENT entregue el código, únicamente debe
 * sustituirse effectiveCountryCode(); el resto del contrato permanece igual.
 */
@ApplicationScoped
public class CoreCatalogService {
    private static final Logger LOG = Logger.getLogger(CoreCatalogService.class);

    @ConfigProperty(name = "sgi.core.catalog-base-url")
    Optional<String> baseUrl;

    @ConfigProperty(name = "sgi.core.country-code", defaultValue = "EC")
    String configuredCountryCode;

    @ConfigProperty(name = "sgi.core.instance-country-code")
    Optional<String> configuredInstanceCountryCode;

    @ConfigProperty(name = "sgi.core.catalog-timeout-ms", defaultValue = "5000")
    int timeoutMs;

    @ConfigProperty(name = "sgi.core.country-cache-seconds", defaultValue = "300")
    long countryCacheSeconds;

    @Inject ObjectMapper mapper;
    @Inject TenantContext tenant;

    private final HttpClient httpClient = CoreHttp.client(Duration.ofSeconds(5));

    private volatile CountryReference cachedCountry;
    private volatile Instant countryCachedAt;

    public record CountryReference(UUID id, String isoAlpha2, String isoAlpha3, String name,
                                   String locale, String timezone, String currency) {}

    public record InstanceCountryReference(UUID id, String code, String name, UUID countryId,
                                           String countryCode, String countryIsoAlpha3,
                                           String countryName) {}

    public record CompanyCatalogItem(UUID id, String code, String name, String description,
                                     String logoUrl, String companyType, String status,
                                     String sourceVersion) {}

    public record ResolvedTenantContext(CountryReference country,
                                        InstanceCountryReference instanceCountry,
                                        int migratedRows) {}

    public ResolvedTenantContext synchronizeTenantContext() {
        CountryReference country = resolveCountry();
        InstanceCountryReference instanceCountry = resolveInstanceCountry(country);
        int migratedRows = tenant.adoptCanonicalInstanceCountry(
            instanceCountry.id(),
            country.id(),
            country.isoAlpha2(),
            country.name(),
            instanceCountry.code(),
            instanceCountry.name()
        );
        if (migratedRows > 0) {
            LOG.infof("Contexto Instancia-País migrado al identificador canónico de CORE; filas actualizadas=%d.", migratedRows);
        }
        return new ResolvedTenantContext(country, instanceCountry, migratedRows);
    }

    public JsonNode territoryGeoJson() {
        CountryReference country = resolveCountry();
        return get(geoJsonPath(country.id()), "application/geo+json, application/json");
    }

    @Transactional
    public void synchronizeTerritoryCatalog() {
        ResolvedTenantContext context = synchronizeTenantContext();
        CountryReference country = context.country();
        JsonNode catalog = get(subdivisionsPath(country.id()), "application/json");
        JsonNode subdivisions = catalog.path("subdivisions");
        if (!subdivisions.isArray()) {
            throw new IllegalStateException("CORE devolvió un catálogo territorial sin subdivisions[].");
        }

        String datasetVersion = requiredText(catalog, "datasetVersion", "catálogo territorial");
        List<CountrySubdivision> local = CountrySubdivision.list("instanceCountryId=?1", tenant.instanceCountryId());
        Map<UUID, CountrySubdivision> byCoreId = local.stream()
            .filter(row -> row.coreSubdivisionId != null)
            .collect(Collectors.toMap(row -> row.coreSubdivisionId, Function.identity(), (left, right) -> left));
        Map<String, CountrySubdivision> byCode = local.stream()
            .collect(Collectors.toMap(row -> normalize(row.code), Function.identity(), (left, right) -> left));
        Map<String, CountrySubdivision> byName = local.stream()
            .collect(Collectors.toMap(row -> normalize(row.name), Function.identity(), (left, right) -> left));
        Set<UUID> received = new HashSet<>();

        for (JsonNode item : subdivisions) {
            UUID coreId = requiredUuid(item, "id", "subdivisión");
            String code = limited(requiredText(item, "code", "subdivisión"), 16, "code");
            String name = limited(requiredText(item, "name", "subdivisión"), 120, "name");
            CountrySubdivision row = byCoreId.get(coreId);
            if (row == null) row = byCode.get(normalize(code));
            if (row == null) row = byName.get(normalize(name));
            if (row == null) {
                row = new CountrySubdivision();
                row.instanceCountryId = tenant.instanceCountryId();
            }

            row.coreSubdivisionId = coreId;
            row.code = code;
            row.officialCode = optionalText(item, "officialCode");
            row.name = name;
            row.subdivisionType = optionalText(item, "type");
            row.sourceVersion = datasetVersion;
            row.status = "ACTIVE";
            if (!row.isPersistent()) row.persist();
            received.add(coreId);
        }

        for (CountrySubdivision row : local) {
            if (row.coreSubdivisionId != null && !received.contains(row.coreSubdivisionId)) row.status = "INACTIVE";
        }
    }

    @Transactional
    public void synchronizeActiveCompanies() {
        ResolvedTenantContext context = synchronizeTenantContext();
        InstanceCountryReference instanceCountry = context.instanceCountry();
        JsonNode response = get(companiesPath(instanceCountry.id()), "application/json");
        if (!response.isArray()) throw new IllegalStateException("CORE devolvió un catálogo de Compañías inválido.");

        List<CoreCompanyCatalogSnapshot> local = CoreCompanyCatalogSnapshot.list("instanceCountryId=?1", tenant.instanceCountryId());
        Map<UUID, CoreCompanyCatalogSnapshot> byCoreId = local.stream()
            .collect(Collectors.toMap(row -> row.coreCompanyId, Function.identity(), (left, right) -> left));
        Map<String, CoreCompanyCatalogSnapshot> byCode = local.stream()
            .collect(Collectors.toMap(row -> normalize(row.code), Function.identity(), (left, right) -> left));
        Set<UUID> received = new HashSet<>();

        for (JsonNode item : response) {
            CompanyCatalogItem core = company(item);
            CoreCompanyCatalogSnapshot row = byCoreId.get(core.id());
            if (row == null) row = byCode.get(normalize(core.code()));
            if (row == null) {
                row = new CoreCompanyCatalogSnapshot();
                row.instanceCountryId = tenant.instanceCountryId();
            } else if (!Objects.equals(row.coreCompanyId, core.id())) {
                UUID previousId = row.coreCompanyId;
                Company.update("coreCatalogId=?1 where instanceCountryId=?2 and coreCatalogId=?3",
                    core.id(), tenant.instanceCountryId(), previousId);
            }

            row.coreCompanyId = core.id();
            row.code = limited(core.code(), 32, "code");
            row.name = limited(core.name(), 160, "name");
            row.historicalReview = limitedNullable(core.description(), 750);
            row.logoDataUrl = core.logoUrl();
            row.companyType = core.companyType() == null || core.companyType().isBlank() ? "SECURITY" : core.companyType();
            row.sourceVersion = limited(core.sourceVersion(), 80, "sourceVersion");
            row.sourceStatus = "ACTIVE";
            row.syncedAt = Instant.now();
            if (!row.isPersistent()) row.persist();
            received.add(core.id());
        }

        for (CoreCompanyCatalogSnapshot row : local) {
            if (!received.contains(row.coreCompanyId)) row.sourceStatus = "INACTIVE";
        }
    }

    public CountryReference resolveCountry() {
        Instant now = Instant.now();
        CountryReference cached = cachedCountry;
        if (cached != null && countryCachedAt != null && now.isBefore(countryCachedAt.plusSeconds(Math.max(1, countryCacheSeconds)))) {
            return cached;
        }
        synchronized (this) {
            cached = cachedCountry;
            if (cached != null && countryCachedAt != null && now.isBefore(countryCachedAt.plusSeconds(Math.max(1, countryCacheSeconds)))) {
                return cached;
            }
            CountryReference resolved = findCountry(get("/catalog/countries", "application/json"), effectiveCountryCode());
            cachedCountry = resolved;
            countryCachedAt = now;
            return resolved;
        }
    }

    String effectiveCountryCode() {
        // Sustituir por el código ISO alpha-2 recibido desde IDENT cuando ese proyecto esté disponible.
        return configuredCountryCode == null || configuredCountryCode.isBlank() ? "EC" : configuredCountryCode.trim().toUpperCase(Locale.ROOT);
    }

    static CountryReference findCountry(JsonNode countries, String isoAlpha2) {
        if (countries == null || !countries.isArray()) throw new IllegalStateException("CORE devolvió un catálogo de países inválido.");
        for (JsonNode item : countries) {
            if (isoAlpha2.equalsIgnoreCase(optionalText(item, "isoAlpha2"))) {
                return new CountryReference(
                    requiredUuid(item, "id", "país"),
                    requiredText(item, "isoAlpha2", "país"),
                    optionalText(item, "isoAlpha3"),
                    requiredText(item, "name", "país"),
                    optionalText(item, "locale"),
                    optionalText(item, "timezone"),
                    optionalText(item, "currency")
                );
            }
        }
        throw new IllegalStateException("CORE no devolvió el país configurado con isoAlpha2=" + isoAlpha2 + ".");
    }

    InstanceCountryReference resolveInstanceCountry(CountryReference country) {
        return findInstanceCountry(
            get(instanceCountriesPath(country.id()), "application/json"),
            country.id(),
            configuredInstanceCountryCode.filter(value -> !value.isBlank()).orElse(null)
        );
    }

    static InstanceCountryReference findInstanceCountry(JsonNode instances, UUID countryId, String configuredCode) {
        if (instances == null || !instances.isArray()) {
            throw new IllegalStateException("CORE devolvió un catálogo de Instancias–País inválido.");
        }
        List<InstanceCountryReference> candidates = new ArrayList<>();
        for (JsonNode item : instances) {
            UUID itemCountryId = requiredUuid(item, "countryId", "Instancia–País");
            if (!countryId.equals(itemCountryId)) continue;
            candidates.add(new InstanceCountryReference(
                requiredUuid(item, "id", "Instancia–País"),
                requiredText(item, "code", "Instancia–País"),
                requiredText(item, "name", "Instancia–País"),
                itemCountryId,
                optionalText(item, "countryCode"),
                optionalText(item, "countryIsoAlpha3"),
                optionalText(item, "countryName")
            ));
        }
        if (configuredCode != null) {
            return candidates.stream()
                .filter(item -> configuredCode.equalsIgnoreCase(item.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "CORE no devolvió la Instancia–País configurada con code=" + configuredCode + "."));
        }
        if (candidates.size() == 1) return candidates.getFirst();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("CORE no devolvió una Instancia–País activa para el país seleccionado.");
        }
        throw new IllegalStateException(
            "CORE devolvió más de una Instancia–País para el país seleccionado; configura SGI_CORE_INSTANCE_COUNTRY_CODE.");
    }

    static String subdivisionsPath(UUID countryId) {
        return "/catalog/subdivisions?countryId=" + enc(countryId.toString());
    }

    static String geoJsonPath(UUID countryId) {
        return "/catalog/subdivisions/geojson?countryId=" + enc(countryId.toString());
    }

    static String instanceCountriesPath(UUID countryId) {
        return "/catalog/instance-countries?countryId=" + enc(countryId.toString());
    }

    static String companiesPath(UUID instanceCountryId) {
        return "/catalog/companies?instanceCountryId=" + enc(instanceCountryId.toString()) + "&status=ACTIVE";
    }

    private CompanyCatalogItem company(JsonNode item) {
        return new CompanyCatalogItem(
            requiredUuid(item, "id", "Compañía"),
            requiredText(item, "code", "Compañía"),
            requiredText(item, "name", "Compañía"),
            optionalText(item, "description"),
            optionalText(item, "logoUrl"),
            optionalText(item, "companyType"),
            optionalText(item, "status"),
            requiredText(item, "sourceVersion", "Compañía")
        );
    }

    private JsonNode get(String path, String accept) {
        String configuredBaseUrl = baseUrl
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException("No está configurada la URL del catálogo CORE."));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(configuredBaseUrl) + path))
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 500)))
                .header("Accept", accept)
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .GET()
                .build();
            HttpResponse<String> response = CoreHttp.send(httpClient, request, "catálogo");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("CORE respondió HTTP " + response.statusCode() + " al consultar " + path + ".");
            }
            return mapper.readTree(response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf(e, "No se pudo consultar el catálogo CORE: %s", path);
            throw new IllegalStateException("No se pudo consultar el catálogo CORE.", e);
        }
    }

    private static String requiredText(JsonNode node, String field, String objectName) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) throw new IllegalStateException("CORE devolvió " + objectName + " sin " + field + ".");
        return value;
    }

    private static UUID requiredUuid(JsonNode node, String field, String objectName) {
        try { return UUID.fromString(requiredText(node, field, objectName)); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("CORE devolvió " + objectName + " con " + field + " inválido.", e); }
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String limited(String value, int max, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("CORE devolvió " + field + " vacío.");
        if (value.length() > max) throw new IllegalStateException("CORE devolvió " + field + " con más de " + max + " caracteres.");
        return value;
    }

    private static String limitedNullable(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }

    private static String trimSlash(String value) {
        String out = value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
