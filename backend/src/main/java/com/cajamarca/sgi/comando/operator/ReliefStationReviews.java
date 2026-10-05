package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.execution.*;
import com.cajamarca.sgi.comando.postconfig.PostOperationalConfig;
import com.cajamarca.sgi.comando.storage.*;
import com.cajamarca.sgi.comando.visint.VisualReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;

/**
 * VISINT opcional en el relevo: si el Puesto lo tiene activado y tiene fotos estándar, cada foto del puesto (station_0..2)
 * queda como una ejecución RELIEF_STATION_CAPTURED con su revisión visual. Nunca bloquea el relevo: el resultado solo se informa.
 */
@ApplicationScoped
public class ReliefStationReviews {
    public static final String TYPE = "RELIEF_STATION_CAPTURED";
    /** Fotos del puesto del relevo y su nombre (stationPhotos del contexto). */
    public static final Map<String,String> STATIONS = Map.of(
        "station_0", "Vista general del puesto", "station_1", "Área de trabajo o garita", "station_2", "Acceso principal");

    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject VisualReviewService reviews;

    /** true si el relevo de este Puesto se valida con VISINT. */
    public boolean enabled(UUID tenant, UUID postId) {
        PostOperationalConfig c = PostOperationalConfig.find("postId=?1 and instanceCountryId=?2", postId, tenant).firstResult();
        return c != null && c.stationVisintEnabled && StandardReferenceImage.countOf(StandardReferenceImage.POST_CONFIG, postId) > 0;
    }

    /** Encola las revisiones en la misma transacción del relevo. Devuelve QUEUED_FOR_VISINT o NOT_REQUESTED. */
    @SuppressWarnings("unchecked")
    public String enqueue(UUID tenant, UUID reliefId, UUID assignmentId, UUID shiftId, UUID pointId, UUID postId, UUID employee, String username,
                          Instant executedAt, JsonNode batch) {
        if (!enabled(tenant, postId)) return "NOT_REQUESTED";
        List<Object[]> photos = em.createNativeQuery("""
            select id,purpose,sha256,content from operator_relief_evidence
            where instance_country_id=:t and event_id=:e and purpose in ('station_0','station_1','station_2') order by purpose""")
            .setParameter("t", tenant).setParameter("e", reliefId).getResultList();
        String status = "NOT_REQUESTED";
        for (Object[] p : photos) {
            UUID photoId = (UUID) p[0];
            String purpose = (String) p[1];
            byte[] bytes = (byte[]) p[3];
            String ct = Optional.ofNullable(ImageSniffer.detect(bytes)).orElse("image/jpeg");

            EvidenceObject o = new EvidenceObject();
            o.id = UUID.randomUUID(); o.instanceCountryId = tenant; o.clientEvidenceId = photoId; o.uploadBatchId = ReliefContract.uuid(batch, "batchId");
            o.eventId = reliefId; o.assignmentId = assignmentId; o.employeeId = employee; o.username = username;
            o.targetType = StandardReferenceImage.POST_CONFIG; o.targetId = postId; o.bucket = storage.evidenceBucket();
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            o.objectKey = String.format("evidence/%04d/%02d/%s/%s.%s", now.getYear(), now.getMonthValue(), reliefId, o.id, ImageSniffer.extension(ct));
            o.contentType = ct; o.sizeBytes = bytes.length; o.sha256 = ((String) p[2]).trim(); o.capturedAt = executedAt;
            o.source = "CAMERA"; o.status = "ATTACHED"; o.receivedAt = Instant.now();
            storage.put(o.bucket, o.objectKey, bytes, ct);
            o.persist();

            TaskExecution x = new TaskExecution();
            x.id = UUID.randomUUID(); x.instanceCountryId = tenant; x.executionType = TYPE; x.assignmentId = assignmentId; x.shiftOccurrenceId = shiftId;
            x.pointId = pointId; x.postId = postId; x.employeeId = employee; x.username = username;
            x.targetType = StandardReferenceImage.POST_CONFIG; x.targetId = postId; x.protocolVersionNo = 0;
            x.groupId = reliefId; x.reliefEvidenceId = photoId; x.executedAt = executedAt; x.receivedAt = Instant.now();
            x.batchId = ReliefContract.uuid(batch, "batchId"); x.correlationId = ReliefContract.uuid(batch, "correlationId"); x.deviceId = ReliefContract.text(batch, "deviceId");
            x.payloadHash = o.sha256; x.payloadJson = "{\"purpose\":\"" + purpose + "\"}"; x.status = "RECEIVED";
            x.persistAndFlush();
            em.createNativeQuery("insert into task_execution_evidence(task_execution_id,evidence_id,sort_order) values(:t,:e,1)")
                .setParameter("t", x.id).setParameter("e", o.id).executeUpdate();
            status = reviews.enqueue(x, StandardReferenceImage.POST_CONFIG, postId, 1).status;
        }
        return status;
    }

    /** Foto del puesto (station_0..2) de una ejecución de relevo; null si no se reconoce. */
    public static String purpose(TaskExecution x) {
        return STATIONS.keySet().stream().filter(k -> x.payloadJson != null && x.payloadJson.contains("\"" + k + "\"")).findFirst().orElse(null);
    }

    /** Foto del puesto de una ejecución de relevo: [código, nombre], p. ej. ["F1", "Vista general del puesto"]. */
    public static String[] station(TaskExecution x) {
        String k = purpose(x);
        return k == null ? new String[]{null, "Foto del puesto"} : new String[]{"F" + (k.charAt(k.length() - 1) - '0' + 1), STATIONS.get(k)};
    }
}
