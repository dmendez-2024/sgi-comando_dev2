package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.*;

/** UAT v1. Only pending-source relief reception is supported. */
public final class ReliefContract {
    public static final Set<String> PURPOSES=Set.of("entrant_face","entrant_full","outgoing_face","outgoing_full","station_0","station_1","station_2");
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
        if(!"PENDING_SOURCE".equals(text(e,"inventoryStatus"))) throw new BadRequestException("Inventario debe permanecer pendiente de su fuente");
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
