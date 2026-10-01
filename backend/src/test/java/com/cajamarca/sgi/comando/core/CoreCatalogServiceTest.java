package com.cajamarca.sgi.comando.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreCatalogServiceTest {
    private static final UUID ECUADOR_ID = UUID.fromString("81000000-0000-0000-0000-000000000007");
    private static final UUID ECUADOR_INSTANCE_ID = UUID.fromString("398233d2-293a-4709-ac30-b74c4269e22d");

    @Test
    void selectsEcuadorByIsoAlpha2AndNotByArrayPosition() throws Exception {
        var countries = new ObjectMapper().readTree("""
            [
              {"id":"81000000-0000-0000-0000-000000000001","isoAlpha2":"AR","isoAlpha3":"ARG","name":"Argentina"},
              {"id":"81000000-0000-0000-0000-000000000007","isoAlpha2":"EC","isoAlpha3":"ECU","name":"Ecuador","locale":"es-EC","timezone":"America/Guayaquil","currency":"USD"}
            ]
            """);

        var country = CoreCatalogService.findCountry(countries, "EC");

        assertEquals(ECUADOR_ID, country.id());
        assertEquals("Ecuador", country.name());
    }

    @Test
    void resolvesTheSingleActiveInstanceCountryForEcuador() throws Exception {
        var instances = new ObjectMapper().readTree("""
            [
              {"id":"398233d2-293a-4709-ac30-b74c4269e22d","code":"ECU-CM","name":"Ecuador - Cajamarca","countryId":"81000000-0000-0000-0000-000000000007","countryCode":"EC","countryIsoAlpha3":"ECU","countryName":"Ecuador"}
            ]
            """);

        var instance = CoreCatalogService.findInstanceCountry(instances, ECUADOR_ID, null);

        assertEquals(ECUADOR_INSTANCE_ID, instance.id());
        assertEquals("ECU-CM", instance.code());
    }

    @Test
    void usesCountryAndInstanceCountryIdsInTheirRespectiveQueries() {
        assertEquals("/catalog/subdivisions?countryId=" + ECUADOR_ID, CoreCatalogService.subdivisionsPath(ECUADOR_ID));
        assertEquals("/catalog/subdivisions/geojson?countryId=" + ECUADOR_ID, CoreCatalogService.geoJsonPath(ECUADOR_ID));
        assertEquals("/catalog/instance-countries?countryId=" + ECUADOR_ID, CoreCatalogService.instanceCountriesPath(ECUADOR_ID));
        assertEquals("/catalog/companies?instanceCountryId=" + ECUADOR_INSTANCE_ID + "&status=ACTIVE", CoreCatalogService.companiesPath(ECUADOR_INSTANCE_ID));
    }
}
