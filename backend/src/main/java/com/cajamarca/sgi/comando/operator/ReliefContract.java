package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.*;

/** Contrato v1 del relevo, incluida la confirmación del inventario entregado por RRMM. */
public final class ReliefContract {
    public static final Set<String> PURPOSES=Set.of("entrant_face","entrant_full","outgoing_face","outgoing_full","station_0","station_1","station_2");
    private static final Set<String> INVENTORY_STATUSES=Set.of("PENDING_SOURCE","REPORTED","COMPLETE");
    private static final Set<String> INVENTORY_CONDITIONS=Set.of("GOOD","DAMAGED","MISSING","REPLACED","NOT_VERIFIED");
    private ReliefContract() {}
    public static String text(JsonNode n,String key) {
        if(n==null || !n.path(key).isTextual() || n.path(key).asText().isBlank()) throw new BadRequestException("Campo requerido: "+key);
        return n.path(key).asText();
    }
    public static UUID uuid(JsonNode n,String key) {
        try { return UUID.fromString(text(n,key)); } catch(IllegalArgumentException e) { throw new BadRequestException("UUID inválido: "+key); }
    }
    public static Instant time(JsonNode n,String key) {
        try { return Instant.parse(text(n,key)); } catch(java.time.format.DateTimeParseException e) { throw new BadRequestException("Fecha inválida: "+key); }
    }
    public static void validate(JsonNode batch,UUID employee,UUID country,Instant now) {
        uuid(batch,"batchId"); uuid(batch,"correlationId");
        if(!employee.equals(uuid(batch,"employeeId")) || !country.equals(uuid(batch,"instanceCountryId"))) throw new jakarta.ws.rs.ForbiddenException("Identidad o instancia incorrecta");
        if(text(batch,"deviceId").length()>120) throw new BadRequestException("deviceId demasiado largo");
        Instant captured=time(batch,"capturedAt");
        if(captured.isAfter(now.plusSeconds(300))) throw new BadRequestException("Fecha de captura futura");
        JsonNode events=batch.path("events");
        if(!events.isArray() || events.size()!=1) throw new BadRequestException("UAT admite un evento de relevo por lote");
        JsonNode e=events.get(0); uuid(e,"eventId"); uuid(e,"assignmentId"); uuid(e,"postId"); uuid(e,"shiftOccurrenceId");
        if(!"RELIEF_SUBMITTED".equals(text(e,"type"))) throw new BadRequestException("Tipo de evento no soportado");
        if(!employee.equals(uuid(e,"incomingEmployeeId"))) throw new jakarta.ws.rs.ForbiddenException("Agente entrante incorrecto");
        String inventoryStatus=text(e,"inventoryStatus").toUpperCase(Locale.ROOT);
        if(!INVENTORY_STATUSES.contains(inventoryStatus)) throw new BadRequestException("Estado de inventario inválido");
        JsonNode inventoryItems=e.path("inventoryItems");
        if("PENDING_SOURCE".equals(inventoryStatus)) {
            if(inventoryItems.isArray() && !inventoryItems.isEmpty()) throw new BadRequestException("No envíe activos mientras el inventario está pendiente de fuente");
        } else {
            if(!inventoryItems.isArray()) throw new BadRequestException("inventoryItems es obligatorio cuando se reporta inventario");
            Set<UUID> inventoryAssets=new HashSet<>();
            for(JsonNode item:inventoryItems) {
                UUID assetId=uuid(item,"assetId");
                if(!inventoryAssets.add(assetId)) throw new BadRequestException("Activo de inventario duplicado: "+assetId);
                String condition=text(item,"condition").toUpperCase(Locale.ROOT);
                if(!INVENTORY_CONDITIONS.contains(condition)) throw new BadRequestException("Condición de inventario inválida: "+condition);
                if(item.hasNonNull("observation") && (!item.path("observation").isTextual() || item.path("observation").asText().length()>1000))
                    throw new BadRequestException("Observación de inventario inválida");
                JsonNode evidenceIds=item.path("evidenceIds");
                if(!evidenceIds.isMissingNode() && !evidenceIds.isArray()) throw new BadRequestException("evidenceIds debe ser una lista");
                Set<UUID> evidence=new HashSet<>();
                if(evidenceIds.isArray()) for(JsonNode id:evidenceIds) {
                    UUID evidenceId;
                    try { evidenceId=UUID.fromString(id.asText()); } catch(Exception ex) { throw new BadRequestException("UUID inválido en evidenceIds"); }
                    if(!evidence.add(evidenceId)) throw new BadRequestException("Evidencia de inventario duplicada");
                }
            }
        }
        if(!e.path("unilateral").isBoolean()) throw new BadRequestException("unilateral debe ser booleano");
        if(e.path("unilateral").asBoolean()) {
            if(text(e,"unilateralReason").length()>1000) throw new BadRequestException("Motivo demasiado largo");
            if(e.hasNonNull("outgoingEmployeeId")) throw new BadRequestException("Relevo unilateral no identifica saliente presente");
        } else if(employee.equals(uuid(e,"outgoingEmployeeId"))) throw new BadRequestException("No se permite auto relevo");
        Instant executed=time(e,"executedAt");
        if(executed.isAfter(captured.plusSeconds(300))) throw new BadRequestException("Ejecución posterior a la captura");
        Set<String> required=new HashSet<>(Set.of("entrant_face","entrant_full","station_0","station_1","station_2"));
        if(!e.path("unilateral").asBoolean()) required.addAll(Set.of("outgoing_face","outgoing_full"));
        JsonNode evidence=e.path("evidence");
        if(!evidence.isArray()) throw new BadRequestException("Evidencias requeridas");
        Set<String> seen=new HashSet<>(); Set<UUID> ids=new HashSet<>();
        for(JsonNode photo:evidence) {
            String purpose=text(photo,"purpose");
            if(!required.contains(purpose) || !seen.add(purpose) || !ids.add(uuid(photo,"evidenceId"))) throw new BadRequestException("Evidencia duplicada o inesperada");
        }
        if(!seen.equals(required)) throw new BadRequestException("Faltan fotografías obligatorias");
        if(!e.path("consignmentReadings").isArray()) throw new BadRequestException("Lecturas requeridas");
    }
}
