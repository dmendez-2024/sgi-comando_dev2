package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.execution.TaskExecution;
import com.cajamarca.sgi.comando.patrols.*;
import com.cajamarca.sgi.comando.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;

/** Encola la revisión visual de una ejecución, en la misma transacción que la ejecución. */
@ApplicationScoped
public class VisualReviewService {
    @Inject StorageService storage;
    @Inject VisintClient visint;

    /** Guarda qué fotos estándar regían al ejecutar (1 a 5, en orden): son las que se envían a VISINT y se muestran después. */
    public VisualReview enqueue(TaskExecution x, PatrolCheckpoint cp) {
        VisualReview r = new VisualReview();
        r.id = UUID.randomUUID();
        r.instanceCountryId = x.instanceCountryId;
        r.taskExecutionId = x.id;
        r.standardTargetType = "PATROL_CHECKPOINT";
        r.standardTargetId = cp.id;
        r.standardImageVersion = cp.standardImageVersion;
        r.simulated = visint.simulated();
        r.correlationId = x.correlationId;
        r.createdAt = Instant.now();
        List<PatrolCheckpointStandardImage> images = PatrolCheckpointStandardImage.of(cp.id);
        if (images.isEmpty()) { r.status = "ERROR_FINAL"; r.lastError = "El Hito no tiene fotos estándar"; }
        else { r.status = "QUEUED_FOR_VISINT"; r.nextAttemptAt = Instant.now(); }
        r.persist();
        for (PatrolCheckpointStandardImage i : images) {
            VisualReviewStandard s = new VisualReviewStandard();
            s.id = UUID.randomUUID(); s.reviewId = r.id; s.position = i.position; s.standardImageId = i.id;
            s.bucket = storage.standardBucket(); s.objectKey = i.objectKey; s.sha256 = i.sha256; s.contentType = i.contentType;
            s.persist();
        }
        return r;
    }

    public String statusFor(UUID taskExecutionId) {
        VisualReview r = VisualReview.find("taskExecutionId", taskExecutionId).firstResult();
        return r == null ? "NOT_REQUESTED" : r.status;
    }
}
