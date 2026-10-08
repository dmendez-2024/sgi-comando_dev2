package com.cajamarca.sgi.comando.inventory;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.integration.ExternalPorts;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Instantánea RRMM, contrato de inventario del Operador y entrega idempotente de reportes. */
@ApplicationScoped
public class InventoryService {
    public static final String EVIDENCE_TARGET = "INVENTORY_ASSET";
    private static final Logger LOG = Logger.getLogger(InventoryService.class);
    private static final Set<String> CONDITIONS = Set.of("GOOD","DAMAGED","MISSING","REPLACED","NOT_VERIFIED");

    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject ExternalPorts.SicRrmmPort rrmm;

    @Transactional
    public ObjectNode runtime(UUID postId) {
        UUID instance = tenant.instanceCountryId();
        try {
            List<Map<String,Object>> assets = rrmm.expectedAssets(instance, postId);
            String sourceVersion = sourceVersion(assets);
            replaceSnapshot(instance, postId, sourceVersion, assets);
            return response(instance, postId, "AVAILABLE", sourceVersion, Instant.now(), null);
        } catch (RuntimeException error) {
            Object[] state = syncState(instance, postId);
            String code = error instanceof InterconnectionException e ? e.code() : "RRMM_UNAVAILABLE";
            if (state == null) return empty("PENDING_SOURCE", code);
            return response(instance, postId, "STALE", state[0].toString(), instant(state[2]), code);
        }
    }

    public boolean isExpectedAsset(UUID postId, UUID assetId) {
        return em.createNativeQuery("select 1 from inventory_expected_asset where instance_country_id=:t and post_id=:p and asset_id=:a and active=true")
            .setParameter("t",tenant.instanceCountryId()).setParameter("p",postId).setParameter("a",assetId).setMaxResults(1).getResultList().size()==1;
    }

    @Transactional
    public String recordReport(JsonNode event, JsonNode inventoryContext, UUID reliefId, UUID assignmentId, UUID postId,
                               UUID pointId, UUID employeeId, String username, UUID correlationId) {
        String submitted = requiredText(event,"inventoryStatus",32).toUpperCase(Locale.ROOT);
        String available = inventoryContext.path("status").asText("PENDING_SOURCE");
        if ("PENDING_SOURCE".equals(submitted)) {
            if (!"PENDING_SOURCE".equals(available)) throw new BadRequestException("El inventario está disponible y debe ser confirmado.");
            if (event.path("inventoryItems").isArray() && !event.path("inventoryItems").isEmpty()) throw new BadRequestException("No envíe activos cuando el inventario está pendiente de fuente.");
            return "NOT_REQUIRED";
        }
        if (!Set.of("REPORTED","COMPLETE").contains(submitted)) throw new BadRequestException("Estado de inventario inválido.");
        if ("PENDING_SOURCE".equals(available)) throw new BadRequestException("SIC:RRMM todavía no ha entregado los activos esperados.");

        Map<UUID,JsonNode> expected = new LinkedHashMap<>();
        for (JsonNode item : inventoryContext.path("items")) expected.put(parseUuid(item,"assetId"),item);
        JsonNode submittedItems = event.path("inventoryItems");
        if (!submittedItems.isArray()) throw new BadRequestException("inventoryItems es obligatorio.");
        Map<UUID,JsonNode> observed = new LinkedHashMap<>();
        for (JsonNode item : submittedItems) {
            UUID assetId=parseUuid(item,"assetId");
            if(observed.put(assetId,item)!=null) throw new BadRequestException("Activo de inventario duplicado: "+assetId);
        }
        if (!observed.keySet().equals(expected.keySet())) throw new BadRequestException("Debe reportar exactamente todos los activos esperados del Puesto.");

        UUID reportId=UUID.randomUUID();
        String sourceVersion=inventoryContext.path("sourceVersion").asText(null);
        ObjectNode outbound=mapper.createObjectNode().put("reportId",reportId.toString()).put("clientRequestId",reliefId.toString())
            .put("correlationId",correlationId.toString()).put("instanceCountryId",tenant.instanceCountryId().toString())
            .put("reliefEventId",reliefId.toString()).put("assignmentId",assignmentId.toString()).put("postId",postId.toString())
            .put("pointId",pointId.toString()).put("employeeId",employeeId.toString()).put("reportedAt",Instant.now().toString())
            .put("sourceVersion",sourceVersion).put("inventoryStatus",submitted);
        ArrayNode outboundItems=outbound.putArray("items");

        for (Map.Entry<UUID,JsonNode> entry : observed.entrySet()) {
            JsonNode item=entry.getValue(), master=expected.get(entry.getKey());
            String condition=requiredText(item,"condition",32).toUpperCase(Locale.ROOT);
            if(!CONDITIONS.contains(condition)) throw new BadRequestException("Condición inválida para el activo "+entry.getKey());
            String observation=optionalText(item,"observation",1000);
            List<UUID> evidenceIds=evidenceIds(item);
            if("DAMAGED".equals(condition)&&evidenceIds.isEmpty()) throw new BadRequestException("El activo dañado requiere al menos una evidencia.");
            for(UUID evidenceId:evidenceIds) requireEvidence(evidenceId,reliefId,assignmentId,entry.getKey(),username);
            ObjectNode out=outboundItems.addObject().put("assetId",entry.getKey().toString()).put("productId",master.path("productId").asText(null))
                .put("code",master.path("code").asText()).put("description",master.path("description").asText())
                .put("expectedCondition",master.path("expectedCondition").asText()).put("condition",condition);
            if(observation!=null)out.put("observation",observation);
            ArrayNode evidence=out.putArray("evidenceIds"); evidenceIds.forEach(id->evidence.add(id.toString()));
        }

        String payload=outbound.toString(), digest=sha256(payload);
        em.createNativeQuery("""
            insert into inventory_report(id,instance_country_id,relief_event_id,assignment_id,post_id,point_id,employee_id,username,
              client_request_id,correlation_id,source_version,inventory_status,reported_at,payload_hash,payload_json,delivery_status,
              delivery_attempts,next_attempt_at,created_at,updated_at)
            values(:id,:t,:relief,:assignment,:post,:point,:employee,:username,:request,:correlation,:version,:status,current_timestamp,
              :hash,:payload,'PENDING',0,current_timestamp,current_timestamp,current_timestamp)
            """).setParameter("id",reportId).setParameter("t",tenant.instanceCountryId()).setParameter("relief",reliefId)
            .setParameter("assignment",assignmentId).setParameter("post",postId).setParameter("point",pointId).setParameter("employee",employeeId)
            .setParameter("username",username).setParameter("request",reliefId).setParameter("correlation",correlationId)
            .setParameter("version",sourceVersion).setParameter("status",submitted).setParameter("hash",digest).setParameter("payload",payload).executeUpdate();

        for (Map.Entry<UUID,JsonNode> entry : observed.entrySet()) {
            JsonNode item=entry.getValue(), master=expected.get(entry.getKey()); UUID itemId=UUID.randomUUID();
            String condition=requiredText(item,"condition",32).toUpperCase(Locale.ROOT), expectedCondition=master.path("expectedCondition").asText("UNKNOWN");
            em.createNativeQuery("""
                insert into inventory_report_item(id,instance_country_id,report_id,asset_id,product_id,asset_code,asset_description,
                  expected_condition,observed_condition,observation,compliant,created_at)
                values(:id,:t,:report,:asset,:product,:code,:description,:expected,:observed,:observation,:compliant,current_timestamp)
                """).setParameter("id",itemId).setParameter("t",tenant.instanceCountryId()).setParameter("report",reportId).setParameter("asset",entry.getKey())
                .setParameter("product",nullableUuid(master.path("productId").asText(null))).setParameter("code",master.path("code").asText())
                .setParameter("description",master.path("description").asText()).setParameter("expected",expectedCondition).setParameter("observed",condition)
                .setParameter("observation",optionalText(item,"observation",1000)).setParameter("compliant",expectedCondition.equalsIgnoreCase(condition)).executeUpdate();
            int evidenceOrder=0; for(UUID evidenceId:evidenceIds(item)) em.createNativeQuery("insert into inventory_report_item_evidence(report_item_id,evidence_id,sort_order) values(:item,:evidence,:sort)")
                .setParameter("item",itemId).setParameter("evidence",evidenceId).setParameter("sort",++evidenceOrder).executeUpdate();
        }
        deliver(reportId);
        return deliveryStatus(reportId);
    }

    public String reportStatus(UUID reliefId) {
        List<?> rows=em.createNativeQuery("select delivery_status from inventory_report where instance_country_id=:t and relief_event_id=:r")
            .setParameter("t",tenant.instanceCountryId()).setParameter("r",reliefId).getResultList();
        return rows.isEmpty()?"NOT_REQUIRED":rows.get(0).toString();
    }

    @Scheduled(every="60s", delayed="15s")
    @Transactional
    void retryPending() {
        @SuppressWarnings("unchecked") List<Object> ids=em.createNativeQuery("select id from inventory_report where delivery_status='PENDING' and next_attempt_at<=current_timestamp order by created_at limit 20").getResultList();
        for(Object id:ids) deliver(UUID.fromString(id.toString()));
    }

    private void deliver(UUID reportId) {
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("select client_request_id,payload_json,delivery_attempts from inventory_report where id=:id and delivery_status='PENDING'")
            .setParameter("id",reportId).getResultList();
        if(rows.isEmpty())return; Object[] row=rows.get(0); int attempts=((Number)row[2]).intValue()+1;
        try {
            Map<String,Object> payload=mapper.readValue(row[1].toString(),Map.class);
            rrmm.reportObservedState(tenant.instanceCountryId(),UUID.fromString(row[0].toString()),payload);
            em.createNativeQuery("update inventory_report set delivery_status='DELIVERED',delivery_attempts=:attempts,delivered_at=current_timestamp,last_error=null,updated_at=current_timestamp where id=:id")
                .setParameter("attempts",attempts).setParameter("id",reportId).executeUpdate();
        } catch(Exception error) {
            int seconds=Math.min(3600,30*(1<<Math.min(attempts-1,6))); String status=attempts>=10?"FAILED":"PENDING";
            em.createNativeQuery("update inventory_report set delivery_status=:status,delivery_attempts=:attempts,next_attempt_at=current_timestamp+(:seconds*interval '1 second'),last_error=:error,updated_at=current_timestamp where id=:id")
                .setParameter("status",status).setParameter("attempts",attempts).setParameter("seconds",seconds)
                .setParameter("error",limit(error.getMessage(),1000)).setParameter("id",reportId).executeUpdate();
            LOG.warnf("rrmm_inventory_delivery report=%s attempt=%d status=%s error=%s",reportId,attempts,status,error.getMessage());
        }
    }

    private void replaceSnapshot(UUID instance,UUID postId,String sourceVersion,List<Map<String,Object>> assets) {
        em.createNativeQuery("update inventory_expected_asset set active=false,updated_at=current_timestamp where instance_country_id=:t and post_id=:p")
            .setParameter("t",instance).setParameter("p",postId).executeUpdate();
        for(Map<String,Object> asset:assets) em.createNativeQuery("""
            insert into inventory_expected_asset(id,instance_country_id,post_id,asset_id,product_id,code,description,expected_condition,
              assignment_status,critical,source_version,source_payload,active,fetched_at,created_at,updated_at)
            values(:id,:t,:post,:asset,:product,:code,:description,:condition,:assignment,:critical,:version,:payload,true,current_timestamp,current_timestamp,current_timestamp)
            on conflict(instance_country_id,post_id,asset_id) do update set product_id=excluded.product_id,code=excluded.code,
              description=excluded.description,expected_condition=excluded.expected_condition,assignment_status=excluded.assignment_status,
              critical=excluded.critical,source_version=excluded.source_version,source_payload=excluded.source_payload,active=true,
              fetched_at=current_timestamp,updated_at=current_timestamp
            """).setParameter("id",UUID.randomUUID()).setParameter("t",instance).setParameter("post",postId).setParameter("asset",asset.get("assetId"))
            .setParameter("product",asset.get("productId")).setParameter("code",asset.get("code")).setParameter("description",asset.get("description"))
            .setParameter("condition",asset.get("expectedCondition")).setParameter("assignment",asset.get("assignmentStatus"))
            .setParameter("critical",asset.get("critical")).setParameter("version",asset.get("sourceVersion")).setParameter("payload",asset.get("sourcePayload")).executeUpdate();
        em.createNativeQuery("""
            insert into inventory_sync_state(instance_country_id,post_id,source_version,item_count,fetched_at)
            values(:t,:p,:version,:count,current_timestamp)
            on conflict(instance_country_id,post_id) do update set source_version=excluded.source_version,item_count=excluded.item_count,fetched_at=current_timestamp
            """).setParameter("t",instance).setParameter("p",postId).setParameter("version",sourceVersion).setParameter("count",assets.size()).executeUpdate();
    }

    private ObjectNode response(UUID instance,UUID postId,String status,String version,Instant fetched,String warning) {
        ObjectNode out=mapper.createObjectNode().put("status",status).put("sourceVersion",version).put("fetchedAt",fetched.toString());
        if(warning!=null)out.put("warning",warning); ArrayNode items=out.putArray("items");
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
            select asset_id,product_id,code,description,expected_condition,assignment_status,critical
            from inventory_expected_asset where instance_country_id=:t and post_id=:p and active=true order by code,asset_id
            """).setParameter("t",instance).setParameter("p",postId).getResultList();
        for(Object[] row:rows){ObjectNode item=items.addObject().put("assetId",row[0].toString());if(row[1]!=null)item.put("productId",row[1].toString());
            item.put("code",row[2].toString()).put("description",row[3].toString()).put("expectedCondition",row[4].toString())
                .put("assignmentStatus",row[5].toString()).put("critical",Boolean.TRUE.equals(row[6]));}
        return out;
    }

    private ObjectNode empty(String status,String warning){ObjectNode out=mapper.createObjectNode().put("status",status);if(warning!=null)out.put("warning",warning);out.putArray("items");return out;}
    private Object[] syncState(UUID instance,UUID postId){List<?> rows=em.createNativeQuery("select source_version,item_count,fetched_at from inventory_sync_state where instance_country_id=:t and post_id=:p")
        .setParameter("t",instance).setParameter("p",postId).getResultList();return rows.isEmpty()?null:(Object[])rows.get(0);}
    private String sourceVersion(List<Map<String,Object>> assets){if(assets.isEmpty())return "RRMM-EMPTY";Set<String> versions=new LinkedHashSet<>();for(var a:assets)versions.add(Objects.toString(a.get("sourceVersion"),""));if(versions.size()!=1||versions.iterator().next().isBlank())throw new InterconnectionException("RRMM_MIXED_SOURCE_VERSION","SIC:RRMM devolvió versiones mezcladas.");return versions.iterator().next();}
    private void requireEvidence(UUID id,UUID event,UUID assignment,UUID asset,String username){List<?> rows=em.createNativeQuery("select 1 from evidence_object where id=:id and instance_country_id=:t and event_id=:event and assignment_id=:assignment and target_type=:type and target_id=:asset and username=:username")
        .setParameter("id",id).setParameter("t",tenant.instanceCountryId()).setParameter("event",event).setParameter("assignment",assignment).setParameter("type",EVIDENCE_TARGET).setParameter("asset",asset).setParameter("username",username).getResultList();if(rows.isEmpty())throw new BadRequestException("Evidencia de inventario no autorizada: "+id);}
    private List<UUID> evidenceIds(JsonNode item){JsonNode values=item.path("evidenceIds");if(values.isMissingNode()||values.isNull())return List.of();if(!values.isArray())throw new BadRequestException("evidenceIds debe ser una lista.");List<UUID> out=new ArrayList<>();Set<UUID> unique=new HashSet<>();for(JsonNode value:values){UUID id;try{id=UUID.fromString(value.asText());}catch(Exception e){throw new BadRequestException("evidenceId inválido.");}if(!unique.add(id))throw new BadRequestException("evidenceId duplicado.");out.add(id);}return out;}
    private String deliveryStatus(UUID reportId){return em.createNativeQuery("select delivery_status from inventory_report where id=:id").setParameter("id",reportId).getSingleResult().toString();}
    private static UUID parseUuid(JsonNode node,String field){try{return UUID.fromString(requiredText(node,field,50));}catch(IllegalArgumentException e){throw new BadRequestException(field+" inválido.");}}
    private static UUID nullableUuid(String value){try{return value==null||value.isBlank()?null:UUID.fromString(value);}catch(Exception e){return null;}}
    private static String requiredText(JsonNode node,String field,int max){String value=optionalText(node,field,max);if(value==null)throw new BadRequestException("Campo obligatorio: "+field);return value;}
    private static String optionalText(JsonNode node,String field,int max){String value=node.path(field).asText("").trim();if(value.isEmpty())return null;if(value.length()>max)throw new BadRequestException(field+" excede el máximo permitido.");return value;}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String limit(String value,int max){if(value==null)return "Error de integración";return value.length()>max?value.substring(0,max):value;}
    private static Instant instant(Object value){if(value instanceof Instant i)return i;if(value instanceof java.time.OffsetDateTime o)return o.toInstant();if(value instanceof java.sql.Timestamp t)return t.toInstant();return Instant.parse(value.toString());}
}
