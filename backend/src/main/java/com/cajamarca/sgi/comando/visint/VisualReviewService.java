package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.execution.TaskExecution;
import com.cajamarca.sgi.comando.storage.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;

/** Encola la revisión visual de una ejecución, en la misma transacción que la ejecución. */
@ApplicationScoped
public class VisualReviewService {
    @Inject StorageService storage;
    @Inject VisintClient visint;
    @org.eclipse.microprofile.config.inject.ConfigProperty(name="sgi.visint.match-threshold", defaultValue="0.8") double defaultThreshold;

    /** Guarda qué fotos estándar regían al ejecutar (1 a 5, en orden): son las que se envían a VISINT y se muestran después. */
    public VisualReview enqueue(TaskExecution x, String targetType, UUID targetId, int standardImageVersion) {
        VisualReview r = new VisualReview();
        r.id = UUID.randomUUID();
        r.instanceCountryId = x.instanceCountryId;
        r.taskExecutionId = x.id;
        r.standardTargetType = targetType;
        r.standardTargetId = targetId;
        r.standardImageVersion = standardImageVersion;
        r.matchThreshold = thresholdOf(targetType, targetId);
        r.simulated = visint.simulated();
        r.correlationId = x.correlationId;
        r.createdAt = Instant.now();
        List<StandardReferenceImage> images = StandardReferenceImage.of(targetType, targetId);
        if (images.isEmpty()) { r.status = "ERROR_FINAL"; r.lastError = "No hay fotos estándar para comparar"; }
        else { r.status = "QUEUED_FOR_VISINT"; r.nextAttemptAt = Instant.now(); }
        r.persist();
        for (StandardReferenceImage i : images) {
            VisualReviewStandard s = new VisualReviewStandard();
            s.id = UUID.randomUUID(); s.reviewId = r.id; s.position = i.position; s.standardImageId = i.id;
            s.bucket = storage.standardBucket(); s.objectKey = i.objectKey; s.sha256 = i.sha256; s.contentType = i.contentType;
            s.persist();
        }
        return r;
    }

    /** Umbral configurado junto a las fotos estándar de la tarea; si no tiene, el predeterminado de SGI. */
    Double thresholdOf(String targetType, UUID targetId) {
        Double t = switch (targetType) {
            case StandardReferenceImage.PATROL_CHECKPOINT -> { var c = com.cajamarca.sgi.comando.patrols.PatrolCheckpoint.<com.cajamarca.sgi.comando.patrols.PatrolCheckpoint>findById(targetId); yield c == null ? null : c.matchThreshold; }
            case StandardReferenceImage.CONSIGNMENT_EVIDENCE -> { var e = com.cajamarca.sgi.comando.consignments.ConsignmentEvidence.<com.cajamarca.sgi.comando.consignments.ConsignmentEvidence>findById(targetId); yield e == null ? null : e.matchThreshold; }
            case StandardReferenceImage.LOGBOOK_FIELD -> { var f = com.cajamarca.sgi.comando.bitacora.LogbookProtocolField.<com.cajamarca.sgi.comando.bitacora.LogbookProtocolField>findById(targetId); yield f == null ? null : f.matchThreshold; }
            case StandardReferenceImage.POST_CONFIG -> { var p = com.cajamarca.sgi.comando.postconfig.PostOperationalConfig.<com.cajamarca.sgi.comando.postconfig.PostOperationalConfig>find("postId", targetId).firstResult(); yield p == null ? null : p.stationMatchThreshold; }
            default -> null;
        };
        return t != null ? t : MatchThreshold.normalize(defaultThreshold);
    }

    public String statusFor(UUID taskExecutionId) {
        VisualReview r = VisualReview.find("taskExecutionId", taskExecutionId).firstResult();
        return r == null ? "NOT_REQUESTED" : r.status;
    }
}
