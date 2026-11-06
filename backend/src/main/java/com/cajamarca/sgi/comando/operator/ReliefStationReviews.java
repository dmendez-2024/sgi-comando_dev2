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
 * Fotos del puesto del relevo (station_0..2, una por foto estándar del Puesto) como ejecuciones RELIEF_STATION_CAPTURED, visibles en Operación, cuando el Puesto
 * valida con VISINT (revisión contra sus fotos estándar) o tiene ubicación GPS (aviso "Fuera del radio GPS" con el GPS del relevo).
 * Nunca bloquea el relevo: los resultados solo se informan.
 */
@ApplicationScoped
public class ReliefStationReviews {
    public static final String TYPE = "RELIEF_STATION_CAPTURED";
    /** Máximo de fotos del puesto en el relevo (station_0..2); coincide con el máximo de fotos estándar del Puesto. */
    public static final int MAX_STATIONS = 3;
    /** Fotos del puesto cuando el Puesto no tiene fotos estándar configuradas. */
    public static final List<String> DEFAULT_STATIONS = List.of("Vista general del puesto", "Área de trabajo o garita", "Acceso principal");

    /** Foto del puesto que pide el relevo: station_N, su título y detalle, y la foto estándar de referencia (null sin fotos estándar). */
    public record Station(String purpose, String title, String detail, UUID standardImageId) {}

    /**
     * Fotos del puesto del relevo: una por cada foto estándar del Puesto (POST_CONFIG, Servicios → Puestos), en su orden,
     * con su título y detalle. Sin fotos estándar (o en puestos de coordinación, postId null) se piden las 3 de siempre.
     */
    public static List<Station> stations(UUID postId) {
        List<StandardReferenceImage> images = postId == null ? List.of() : StandardReferenceImage.of(StandardReferenceImage.POST_CONFIG, postId);
        List<Station> out = new ArrayList<>();
        if (images.isEmpty()) {
            for (int i = 0; i < DEFAULT_STATIONS.size(); i++) out.add(new Station("station_" + i, DEFAULT_STATIONS.get(i), null, null));
            return out;
        }
        for (int i = 0; i < Math.min(images.size(), MAX_STATIONS); i++) {
            StandardReferenceImage img = images.get(i);
            String title = img.title == null || img.title.isBlank() ? "Foto " + (i + 1) + " del puesto" : img.title.trim();
            String detail = img.detail == null || img.detail.isBlank() ? null : img.detail.trim();
            out.add(new Station("station_" + i, title, detail, img.id));
        }
        return out;
    }

    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject VisualReviewService reviews;
    @Inject com.cajamarca.sgi.comando.settings.EvidenceLocationSettings locationSettings;

    /** true si el relevo de este Puesto se valida con VISINT. */
    public boolean enabled(UUID tenant, UUID postId) {
        PostOperationalConfig c = PostOperationalConfig.find("postId=?1 and instanceCountryId=?2", postId, tenant).firstResult();
        return c != null && c.stationVisintEnabled && StandardReferenceImage.countOf(StandardReferenceImage.POST_CONFIG, postId) > 0;
    }

    /** Registra las fotos del puesto y encola sus revisiones en la misma transacción del relevo. Devuelve QUEUED_FOR_VISINT o NOT_REQUESTED. */
    @SuppressWarnings("unchecked")
    public String enqueue(UUID tenant, UUID reliefId, UUID assignmentId, UUID shiftId, UUID pointId, UUID postId, UUID employee, String username,
                          Instant executedAt, JsonNode batch) {
        boolean visint = enabled(tenant, postId);
        double[] post = locationSettings.postReference(tenant, postId);
        if (!visint && post == null) return "NOT_REQUESTED";
        // GPS del agente al hacer el relevo (opcional en el evento): se compara con la ubicación del Puesto.
        JsonNode event = batch.path("events").path(0);
        Double lat = event.path("latitude").isNumber() ? event.path("latitude").asDouble() : null;
        Double lng = event.path("longitude").isNumber() ? event.path("longitude").asDouble() : null;
        Double acc = event.path("accuracyM").isNumber() ? event.path("accuracyM").asDouble() : null;
        Integer distance = post != null && lat != null && lng != null ? (int) Math.round(GeoDistance.meters(lat, lng, post[0], post[1])) : null;
        boolean far = distance != null && distance > post[2];
        List<Object[]> photos = em.createNativeQuery("""
            select id,purpose,sha256,bucket,object_key,size_bytes,content from operator_relief_evidence
            where instance_country_id=:t and event_id=:e and purpose in ('station_0','station_1','station_2') order by purpose""")
            .setParameter("t", tenant).setParameter("e", reliefId).getResultList();
        String status = "NOT_REQUESTED";
        for (Object[] p : photos) {
            UUID photoId = (UUID) p[0];
            String purpose = (String) p[1];

            EvidenceObject o = new EvidenceObject();
            o.id = UUID.randomUUID(); o.instanceCountryId = tenant; o.clientEvidenceId = photoId; o.uploadBatchId = ReliefContract.uuid(batch, "batchId");
            o.eventId = reliefId; o.assignmentId = assignmentId; o.employeeId = employee; o.username = username;
            o.targetType = StandardReferenceImage.POST_CONFIG; o.targetId = postId;
            if (p[4] != null) {
                // La foto ya está en MinIO (la subió el PUT del relevo): se referencia el mismo objeto, sin copiarlo.
                o.bucket = (String) p[3]; o.objectKey = (String) p[4]; o.contentType = ReliefEvidencePhotos.CONTENT_TYPE; o.sizeBytes = ((Number) p[5]).longValue();
            } else {
                // Foto antigua guardada en content: se sube una copia como antes.
                byte[] bytes = (byte[]) p[6];
                String ct = Optional.ofNullable(ImageSniffer.detect(bytes)).orElse("image/jpeg");
                ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                o.bucket = storage.evidenceBucket();
                o.objectKey = String.format("evidence/%04d/%02d/%s/%s.%s", now.getYear(), now.getMonthValue(), reliefId, o.id, ImageSniffer.extension(ct));
                o.contentType = ct; o.sizeBytes = bytes.length;
                storage.put(o.bucket, o.objectKey, bytes, ct);
            }
            o.sha256 = ((String) p[2]).trim(); o.capturedAt = executedAt;
            o.latitude = lat; o.longitude = lng; o.accuracyM = acc; o.flags = far ? "OUT_OF_RANGE" : "";
            o.referenceDistanceM = distance; o.referenceRadiusM = distance == null ? null : (int) post[2];
            o.source = "CAMERA"; o.status = "ATTACHED"; o.receivedAt = Instant.now();
            o.persist();

            TaskExecution x = new TaskExecution();
            x.id = UUID.randomUUID(); x.instanceCountryId = tenant; x.executionType = TYPE; x.assignmentId = assignmentId; x.shiftOccurrenceId = shiftId;
            x.pointId = pointId; x.postId = postId; x.employeeId = employee; x.username = username;
            x.targetType = StandardReferenceImage.POST_CONFIG; x.targetId = postId; x.protocolVersionNo = 0;
            x.groupId = reliefId; x.reliefEvidenceId = photoId; x.executedAt = executedAt; x.receivedAt = Instant.now();
            x.latitude = lat; x.longitude = lng; x.accuracyM = acc;
            x.batchId = ReliefContract.uuid(batch, "batchId"); x.correlationId = ReliefContract.uuid(batch, "correlationId"); x.deviceId = ReliefContract.text(batch, "deviceId");
            x.payloadHash = o.sha256; x.payloadJson = "{\"purpose\":\"" + purpose + "\"}"; x.status = "RECEIVED";
            x.persistAndFlush();
            em.createNativeQuery("insert into task_execution_evidence(task_execution_id,evidence_id,sort_order) values(:t,:e,1)")
                .setParameter("t", x.id).setParameter("e", o.id).executeUpdate();
            if (visint) status = reviews.enqueue(x, StandardReferenceImage.POST_CONFIG, postId, 1).status;
        }
        return status;
    }

    /** Foto del puesto (station_0..2) de una ejecución de relevo; null si no se reconoce. */
    public static String purpose(TaskExecution x) {
        for (int i = 0; i < MAX_STATIONS; i++) {
            String k = "station_" + i;
            if (x.payloadJson != null && x.payloadJson.contains("\"" + k + "\"")) return k;
        }
        return null;
    }

    /**
     * Foto del puesto de una ejecución de relevo: [código, nombre], p. ej. ["F1", "Entrada principal"]. El nombre sale de
     * stationPhotos del contexto que se guardó con el relevo (el que vio el agente); relevos sin ese dato usan los nombres de siempre.
     */
    public static String[] station(TaskExecution x, EntityManager em) {
        String k = purpose(x);
        if (k == null) return new String[]{null, "Foto del puesto"};
        int index = k.charAt(k.length() - 1) - '0';
        return new String[]{"F" + (index + 1), name(names(em, x.instanceCountryId, x.groupId), k)};
    }

    /** Nombres de las fotos del puesto (stationPhotos) del contexto guardado con el relevo; vacío si el relevo no lo trae. */
    public static List<String> names(EntityManager em, UUID tenant, UUID reliefId) {
        if (reliefId == null) return List.of();
        List<?> rows = em.createNativeQuery("select context_json from operator_relief_submission where id=:id and instance_country_id=:t")
            .setParameter("id", reliefId).setParameter("t", tenant).getResultList();
        if (rows.isEmpty() || rows.get(0) == null) return List.of();
        try {
            List<String> out = new ArrayList<>();
            for (JsonNode n : MAPPER.readTree(rows.get(0).toString()).path("stationPhotos")) out.add(n.asText(""));
            return out;
        } catch (Exception e) { return List.of(); } // contexto ilegible: nombres de siempre
    }

    /** Nombre de la foto del puesto station_N: el del contexto del relevo o, si no está, el de siempre; null si no es foto del puesto. */
    public static String name(List<String> names, String purpose) {
        if (purpose == null || !purpose.matches("station_[0-9]")) return null;
        int index = purpose.charAt(purpose.length() - 1) - '0';
        if (index < names.size() && !names.get(index).isBlank()) return names.get(index);
        return index < DEFAULT_STATIONS.size() ? DEFAULT_STATIONS.get(index) : "Foto del puesto";
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
}
