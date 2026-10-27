package com.cajamarca.sgi.comando.smc;

import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Solo 200/201 con aceptación explícita y recibo válido confirman la entrega. */
public record SmcDeliveryOutcome(String status, String smcFactId, String error) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static SmcDeliveryOutcome from(IntegrationResult result) {
        JsonNode body = null;
        try { if (result.body != null) body = JSON.readTree(result.body); } catch (Exception ignored) { }
        if (result.statusCode == 200 || result.statusCode == 201) {
            JsonNode id = body == null ? null : body.get("smcFactId");
            if (body != null && body.path("accepted").isBoolean() && body.path("accepted").booleanValue()
                && id != null && id.isTextual() && !id.asText().isBlank() && id.asText().length() <= 64)
                return new SmcDeliveryOutcome("SENT", id.asText(), null);
            return new SmcDeliveryOutcome("PENDING", null, "INVALID_SMC_ACK: falta accepted=true o smcFactId válido");
        }
        int code = result.statusCode;
        boolean retry = code == 401 || code == 403 || code == 408 || code == 429 || code >= 500 || code < 400;
        String error = "HTTP_" + code;
        if (body != null && body.isObject()) {
            error += ": " + body.path("code").asText("") + " " + body.path("message").asText("");
        }
        return new SmcDeliveryOutcome(retry ? "PENDING" : "REJECTED", null,
            error.substring(0, Math.min(error.length(), 2000)));
    }

    public static long backoffSeconds(int attempts) {
        return Math.min(3600L, 30L * (1L << Math.min(7, Math.max(0, attempts - 1))));
    }
}
