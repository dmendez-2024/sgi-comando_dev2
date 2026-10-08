package com.cajamarca.sgi.comando.integration;

import com.cajamarca.sgi.comando.interconnections.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

/** Adaptador de inventario. La URL y credencial efectiva siempre se resuelven en CORE. */
@ApplicationScoped
public class SicRrmmAdapter implements ExternalPorts.SicRrmmPort {
    private static final String EXPECTED_INTERFACE = "SGI_COM_SIC_RRMM_0001_IF01";
    private static final String REPORT_INTERFACE = "SGI_COM_SIC_RRMM_0002_IF01";

    @Inject GenericInterconnectionExecutor executor;
    @Inject ObjectMapper mapper;

    @Override
    public List<Map<String,Object>> expectedAssets(UUID instanceCountryId, UUID postId) {
        IntegrationRequest request = new IntegrationRequest();
        request.pathParams.put("postId", postId.toString());
        request.queryParams.put("instanceCountryId", instanceCountryId.toString());
        IntegrationResult result = executor.execute(InterconnectionIds.RRMM_EXPECTED_ASSETS, EXPECTED_INTERFACE, request);
        if (!result.successful()) throw new InterconnectionException("RRMM_EXPECTED_ASSETS_HTTP_" + result.statusCode,
            "SIC:RRMM respondió HTTP " + result.statusCode + " al consultar los activos del Puesto.");
        try {
            JsonNode root = mapper.readTree(result.body);
            JsonNode items = root != null && root.isArray() ? root : first(root, "items", "assets", "data");
            if (items == null || !items.isArray()) throw new IllegalArgumentException("items ausente");
            String responseVersion = text(root, "sourceVersion");
            List<Map<String,Object>> assets = new ArrayList<>();
            for (JsonNode item : items) {
                UUID assetId = uuid(item, "assetId", true);
                UUID productId = uuid(item, "productId", false);
                String code = required(item, "code", 100);
                String description = required(item, "description", 300);
                String sourceVersion = optional(item, "sourceVersion", 120);
                if (sourceVersion == null) sourceVersion = responseVersion;
                if (sourceVersion == null || sourceVersion.isBlank()) throw new IllegalArgumentException("sourceVersion ausente");
                Map<String,Object> asset = new LinkedHashMap<>();
                asset.put("assetId", assetId);
                asset.put("productId", productId);
                asset.put("code", code);
                asset.put("description", description);
                asset.put("expectedCondition", optional(item, "expectedCondition", 32) == null ? value(item,"condition","UNKNOWN") : optional(item,"expectedCondition",32));
                asset.put("assignmentStatus", optional(item, "assignmentStatus", 32) == null ? value(item,"status","ASSIGNED") : optional(item,"assignmentStatus",32));
                asset.put("critical", item.path("critical").asBoolean(false));
                asset.put("sourceVersion", sourceVersion);
                asset.put("sourcePayload", item.toString());
                assets.add(asset);
            }
            return assets;
        } catch (InterconnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new InterconnectionException("RRMM_INVALID_EXPECTED_ASSETS", "SIC:RRMM devolvió un inventario inválido.", e);
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
