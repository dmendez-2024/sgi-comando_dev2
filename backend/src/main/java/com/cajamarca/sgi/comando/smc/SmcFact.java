package com.cajamarca.sgi.comando.smc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Hecho operacional, nunca una nota ni una adjudicación de Impulsos. */
public record SmcFact(UUID tenant, String sourceType, UUID sourceId, UUID employeeId,
                      String factType, Instant occurredAt, BigDecimal value,
                      UUID pointId, UUID postId, UUID shiftId, Map<String,Object> metadata, Long personaId) {
    public UUID eventId() {
        // Formato acordado con SMC. No cambia al reintentar ni al reiniciar el proceso.
        return UUID.nameUUIDFromBytes(("SGI_COM:" + sourceType + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
    }

    public Map<String,Object> payload() {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId().toString());
        body.put("instanceCountryId", tenant.toString());
        body.put("employeeId", employeeId.toString());
        if (personaId != null) body.put("persona_id", personaId);
        body.put("factType", factType);
        body.put("occurredAt", occurredAt.toString());
        body.put("value", value);
        if (pointId != null) body.put("pointId", pointId.toString());
        if (postId != null) body.put("postId", postId.toString());
        if (shiftId != null) body.put("shiftId", shiftId.toString());
        body.put("metadata", metadata);
        body.put("correlationId", eventId().toString());
        return body;
    }

    public static BigDecimal percentage(long completed, long total) {
        if (total <= 0 || completed < 0 || completed > total)
            throw new IllegalArgumentException("Conteo de hitos inválido");
        return BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
