package com.cajamarca.sgi.comando.ident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentRolePublisherCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception { return mapper.readTree(s); }

    private JsonNode resource(String path) throws Exception {
        try (InputStream in = IdentRolePublisher.class.getResourceAsStream(path)) { return mapper.readTree(in); }
    }

    @Test
    void comandoYOperadorRecibenLosComunesMasLosPropios() throws Exception {
        JsonNode common = json("{\"roles\":[{\"code\":\"A\",\"name\":\"A\",\"scopeRequirement\":\"PE\"}]}");
        JsonNode soloOperador = json("{\"roles\":[{\"code\":\"B\",\"name\":\"B\",\"scopeRequirement\":\"PE\"}]}");
        JsonNode vacio = json("{\"roles\":[]}");

        List<Map<String, Object>> comando = IdentRolePublisher.payloads(IdentRolePublisher.catalog(common, vacio));
        List<Map<String, Object>> operador = IdentRolePublisher.payloads(IdentRolePublisher.catalog(common, soloOperador));

        assertEquals(List.of("A"), comando.stream().map(r -> r.get("role_code")).toList());
        assertEquals(List.of("A", "B"), operador.stream().map(r -> r.get("role_code")).toList());
    }

    @Test
    void unRolRepetidoEntreArchivosEsError() throws Exception {
        JsonNode common = json("{\"roles\":[{\"code\":\"A\",\"name\":\"A\",\"scopeRequirement\":\"PE\"}]}");
        assertThrows(IllegalStateException.class, () -> IdentRolePublisher.catalog(common, common));
    }

    @Test
    void hoyAmbosSistemasPublicanLosMismosRoles() throws Exception {
        JsonNode common = resource(IdentRolePublisher.COMMON);
        var comando = IdentRolePublisher.payloads(IdentRolePublisher.catalog(common, resource(IdentRolePublisher.EXTRA.get(IdentRolePublisher.COM))));
        var operador = IdentRolePublisher.payloads(IdentRolePublisher.catalog(common, resource(IdentRolePublisher.EXTRA.get(IdentRolePublisher.OPR))));
        assertEquals(13, comando.size());
        assertEquals(comando, operador);
    }

    @Test
    void laDireccionDeOperadorSoloCambiaElSistema() {
        String com = "https://apps.cajamarca.ec/dev.ident/api/v1/integrations/programs/SGI_COM/roles";
        assertEquals(com, IdentRolePublisher.programUrl(com, "SGI_COM"));
        assertEquals("https://apps.cajamarca.ec/dev.ident/api/v1/integrations/programs/SGI_OPR/roles", IdentRolePublisher.programUrl(com, "SGI_OPR"));
        assertThrows(IllegalStateException.class, () -> IdentRolePublisher.programUrl("https://x/otra/ruta", "SGI_OPR"));
    }
}
