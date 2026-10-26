package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.*;

/** Contrato v1 del relevo, incluida la confirmación del inventario entregado por RRMM. */
public final class ReliefContract {
    public static final Set<String> PURPOSES=Set.of("entrant_face","entrant_full","outgoing_face","outgoing_full","station_0","station_1","station_2");
    /** El relevo llegó sin inventario (sin inventoryStatus ni activos): el inventario es opcional. */
    public static final String INVENTORY_NOT_REPORTED="NOT_REPORTED";
    private static final Set<String> INVENTORY_STATUSES=Set.of("PENDING_SOURCE","REPORTED","COMPLETE",INVENTORY_NOT_REPORTED);
    /** Estados con los que el relevo se acepta sin activos, aunque RRMM tenga inventario del puesto. */
    public static final Set<String> INVENTORY_WITHOUT_ITEMS=Set.of("PENDING_SOURCE",INVENTORY_NOT_REPORTED);
    private static final Set<String> INVENTORY_CONDITIONS=Set.of("GOOD","DAMAGED","MISSING","REPLACED","NOT_VERIFIED");
    /** Relevo con agente saliente presente (turno anterior inmediato de otro agente). */
    public static final String MODE_NORMAL="NORMAL";
    /** Saliente esperado ausente; el agente escribe el motivo. */
    public static final String MODE_UNILATERAL="UNILATERAL";
    /** No hay turno anterior inmediato de otro agente: el agente inicia el puesto sin saliente. */
    public static final String MODE_OPENING="OPENING_WITHOUT_PREVIOUS_SHIFT";
    /** Causas formales del inicio sin turno precedente; Comando las calcula y la app las devuelve tal cual. */
    public static final String CAUSE_NO_PREVIOUS_SHIFT="NO_PREVIOUS_SHIFT";
    public static final String CAUSE_GAP="PREVIOUS_SHIFT_NOT_CONSECUTIVE";
    public static final String CAUSE_SAME_AGENT="PREVIOUS_SHIFT_SAME_AGENT";
    public static final Set<String> OPENING_CAUSES=Set.of(CAUSE_NO_PREVIOUS_SHIFT,CAUSE_GAP,CAUSE_SAME_AGENT);
    private ReliefContract() {}
    /** Modo del evento; sin reliefMode (apps anteriores) se deduce de unilateral. */
    public static String mode(JsonNode event) {
        if(!event.hasNonNull("reliefMode")) return event.path("unilateral").asBoolean()?MODE_UNILATERAL:MODE_NORMAL;
        return event.path("reliefMode").asText();
    }
    /** inventoryStatus del evento; si no viene (inventario no enviado) es NOT_REPORTED. */
    public static String inventoryStatus(JsonNode event) {
        JsonNode status=event.path("inventoryStatus");
        if(status.isMissingNode() || status.isNull() || (status.isTextual() && status.asText().isBlank())) return INVENTORY_NOT_REPORTED;
        return text(event,"inventoryStatus").toUpperCase(Locale.ROOT);
    }
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
        String inventoryStatus=inventoryStatus(e);
        if(!INVENTORY_STATUSES.contains(inventoryStatus)) throw new BadRequestException("Estado de inventario inválido");
        JsonNode inventoryItems=e.path("inventoryItems");
        if(INVENTORY_WITHOUT_ITEMS.contains(inventoryStatus)) {
            if(inventoryItems.isArray() && !inventoryItems.isEmpty()) throw new BadRequestException(INVENTORY_NOT_REPORTED.equals(inventoryStatus)
                ? "inventoryStatus es obligatorio cuando se envían activos de inventario" : "No envíe activos mientras el inventario está pendiente de fuente");
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
        String mode=mode(e);
        if(!Set.of(MODE_NORMAL,MODE_UNILATERAL,MODE_OPENING).contains(mode)) throw new BadRequestException("reliefMode inválido");
        if(e.path("unilateral").asBoolean()!=MODE_UNILATERAL.equals(mode)) throw new BadRequestException("unilateral no corresponde a reliefMode");
        switch(mode) {
            case MODE_UNILATERAL -> {
                if(text(e,"unilateralReason").length()>1000) throw new BadRequestException("Motivo demasiado largo");
                if(e.hasNonNull("outgoingEmployeeId")) throw new BadRequestException("Relevo unilateral no identifica saliente presente");
            }
            case MODE_OPENING -> {
                if(!OPENING_CAUSES.contains(text(e,"openingCause"))) throw new BadRequestException("openingCause inválida");
                if(e.hasNonNull("outgoingEmployeeId")) throw new BadRequestException("El inicio sin turno anterior no identifica saliente");
            }
            default -> { if(employee.equals(uuid(e,"outgoingEmployeeId"))) throw new BadRequestException("No se permite auto relevo"); }
        }
        Instant executed=time(e,"executedAt");
        if(executed.isAfter(captured.plusSeconds(300))) throw new BadRequestException("Ejecución posterior a la captura");
        Set<String> required=new HashSet<>(Set.of("entrant_face","entrant_full","station_0","station_1","station_2"));
        if(MODE_NORMAL.equals(mode)) required.addAll(Set.of("outgoing_face","outgoing_full"));
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
