package com.cajamarca.sgi.comando.visint;

import java.time.Instant;
import java.util.*;

/** Contrato con VISINT: una llamada síncrona por ejecución que compara la foto del agente con las fotos estándar del Hito (1 a 5). */
public interface VisintPort {
    /** La foto que tomó el agente (una por Hito). */
    record EvidenceRef(UUID evidenceId, String bucket, String objectKey, String sha256, String contentType, Instant capturedAt, Double latitude, Double longitude) {}
    /** Una foto estándar del Hito, en su posición (1..5). */
    record StandardRef(UUID imageId, int position, String bucket, String objectKey, String sha256, String contentType) {}
    /** serviceId: ronda de patrulla; activityId: Hito. attempt: número de intento (1, 2…). */
    record ReviewRequest(UUID reviewId, int attempt, UUID taskExecutionId, String taskType, UUID employeeId, UUID companyId,
                         UUID pointId, UUID postId, UUID serviceId, UUID activityId, EvidenceRef evidence, List<StandardRef> standards, UUID correlationId) {}
    /**
     * result: "PASS" (cumple), "FAIL" (no cumple) o "ERROR" (VISINT no pudo evaluar). Sin puntajes.
     * matchedStandardImageId: foto estándar que coincidió (matchedReferenceId); reasonCode y modelVersion tal como los devuelve VISINT.
     */
    record ReviewResult(String externalId, String result, String findings, UUID matchedStandardImageId, String reasonCode, String modelVersion) {}

    /** Llamada síncrona. Lanza VisintUnavailableException si VISINT no responde, responde TIMEOUT/PROCESSING o algo inválido (se reintenta). */
    ReviewResult review(ReviewRequest request);
}
