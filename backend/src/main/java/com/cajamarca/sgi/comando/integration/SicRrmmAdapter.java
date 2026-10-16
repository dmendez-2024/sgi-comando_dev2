package com.cajamarca.sgi.comando.integration;

import com.cajamarca.sgi.comando.interconnections.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Adaptador de inventario. La URL y credencial efectiva siempre se resuelven en CORE. */
@ApplicationScoped
public class SicRrmmAdapter implements ExternalPorts.SicRrmmPort {
    private static final String EXPECTED_INTERFACE = "SGI_COM_SIC_RRMM_0001_IF01";
    private static final String REPORT_INTERFACE = "SGI_COM_SIC_RRMM_0002_IF01";

    @Inject GenericInterconnectionExecutor executor;
    @Inject ObjectMapper mapper;

    @Override
    public List<Map<String,Object>> expectedAssets(UUID instanceCountryId, String postReference) {
        if (postReference == null || postReference.isBlank())
            throw new InterconnectionException("RRMM_POST_REFERENCE_MISSING", "El Puesto no tiene una referencia para consultar SIC:RRMM.");
        IntegrationRequest request = new IntegrationRequest();
        request.pathParams.put("idPuesto", postReference.trim());
        request.queryParams.put("holderType", "POSITION");
        request.queryParams.put("holderReference", postReference.trim());
        IntegrationResult result = executor.execute(InterconnectionIds.RRMM_EXPECTED_ASSETS, EXPECTED_INTERFACE, request);
        if (!result.successful()) throw new InterconnectionException("RRMM_EXPECTED_ASSETS_HTTP_" + result.statusCode,
            "SIC:RRMM respondió HTTP " + result.statusCode + " al consultar los activos del Puesto.");
        return parseHoldings(result.body, postReference.trim());
    }

    List<Map<String,Object>> parseHoldings(String body, String postReference) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode items = root == null ? null : root.path("items");
            if (items == null || !items.isArray()) throw new IllegalArgumentException("items ausente");
            List<Map<String,Object>> holdings = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (JsonNode item : items) {
                String type = required(item, "recordType", 32);
                if (!Set.of("IDENTIFIED", "FUNGIBLE", "LOTTED").contains(type)) throw new IllegalArgumentException("recordType inválido");
                if (!"POSITION".equals(required(item, "holderType", 32)) ||
                    !postReference.equalsIgnoreCase(required(item, "holderReference", 120)))
                    throw new IllegalArgumentException("tenencia de otro Puesto");
                UUID recordId = uuid(item, "recordId", true);
                if (!seen.add(recordId)) throw new IllegalArgumentException("recordId duplicado");
                UUID productId = uuid(item, "productId", true);
                String code = "IDENTIFIED".equals(type) ? required(item, "assetCode", 100) : required(item, "productCode", 100);
                String description = required(item, "productName", 300);
                String status = required(item, "status", 32);
                if (!"ASSIGNED".equals(status) || item.path("quantity").decimalValue().signum() <= 0)
                    throw new IllegalArgumentException("tenencia sin asignación vigente");
                Map<String,Object> holding = new LinkedHashMap<>();
                holding.put("assetId", recordId);
                holding.put("productId", productId);
                holding.put("code", code);
                holding.put("description", description);
                holding.put("expectedCondition", "UNKNOWN");
                holding.put("assignmentStatus", status);
                holding.put("critical", false);
                holding.put("recordType", type);
                holding.put("quantity", item.path("quantity").decimalValue());
                holding.put("serialNumber", optional(item, "serialNumber", 120));
                holding.put("lotCode", optional(item, "lotCode", 120));
                holding.put("sourcePayload", item.toString());
                holdings.add(holding);
            }
            holdings.sort(Comparator.comparing(h -> h.get("assetId").toString()));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map<String,Object> holding : holdings) digest.update(holding.get("sourcePayload").toString().getBytes(StandardCharsets.UTF_8));
            String version = "RRMM-HOLDINGS-" + HexFormat.of().formatHex(digest.digest());
            for (Map<String,Object> holding : holdings) holding.put("sourceVersion", version);
            return holdings;
        } catch (Exception e) {
            throw new InterconnectionException("RRMM_INVALID_HOLDINGS", "SIC:RRMM devolvió tenencias inválidas para el Puesto.", e);
        }
    }

    @Override
    public Map<String,Object> reportObservedState(UUID instanceCountryId, UUID clientRequestId, Map<String,Object> report) {
        IntegrationRequest request = new IntegrationRequest();
        request.body = report;
        request.idempotencyKey = clientRequestId.toString();
        request.correlationId = Objects.toString(report.get("correlationId"), null);
        IntegrationResult result = executor.execute(InterconnectionIds.RRMM_STATE_REPORTS, REPORT_INTERFACE, request);
        if (!result.successful()) throw new InterconnectionException("RRMM_STATE_REPORT_HTTP_" + result.statusCode,
            "SIC:RRMM respondió HTTP " + result.statusCode + " al recibir el reporte de inventario.");
        try {
            if (result.body == null || result.body.isBlank()) return Map.of("accepted", true, "correlationId", result.correlationId);
            return mapper.convertValue(mapper.readTree(result.body), Map.class);
        } catch (Exception e) {
            throw new InterconnectionException("RRMM_INVALID_STATE_REPORT_RESPONSE", "SIC:RRMM devolvió una respuesta inválida.", e);
        }
    }

    private static JsonNode first(JsonNode node,String... names){if(node==null)return null;for(String name:names){JsonNode v=node.get(name);if(v!=null&&!v.isNull())return v;}return null;}
    private static String text(JsonNode node,String name){JsonNode v=node==null?null:node.get(name);return v==null||v.isNull()||v.isContainerNode()?null:v.asText().trim();}
    private static String optional(JsonNode node,String name,int max){String v=text(node,name);if(v==null||v.isBlank())return null;if(v.length()>max)throw new IllegalArgumentException(name+" excede el máximo");return v;}
    private static String required(JsonNode node,String name,int max){String v=optional(node,name,max);if(v==null)throw new IllegalArgumentException(name+" obligatorio");return v;}
    private static String value(JsonNode node,String name,String fallback){String v=text(node,name);return v==null||v.isBlank()?fallback:v;}
    private static UUID uuid(JsonNode node,String name,boolean required){String v=text(node,name);if((v==null||v.isBlank())&&!required)return null;try{return UUID.fromString(v);}catch(Exception e){throw new IllegalArgumentException(name+" inválido");}}
}
