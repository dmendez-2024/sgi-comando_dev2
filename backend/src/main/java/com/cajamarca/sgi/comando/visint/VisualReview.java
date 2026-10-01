package com.cajamarca.sgi.comando.visint;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Revisión visual de una ejecución: qué se envió a VISINT (snapshot de la foto estándar) y qué respondió. */
@Entity
@Table(name="visual_review")
public class VisualReview extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name="instance_country_id", nullable=false) public UUID instanceCountryId;
    @Column(name="task_execution_id", nullable=false) public UUID taskExecutionId;
    @Column(name="status", nullable=false, length=24) public String status;
    @Column(name="standard_target_type", nullable=false, length=40) public String standardTargetType;
    @Column(name="standard_target_id", nullable=false) public UUID standardTargetId;
    @Column(name="standard_image_version", nullable=false) public int standardImageVersion;
    @Column(name="simulated", nullable=false) public boolean simulated;
    @Column(name="visint_external_id", length=120) public String visintExternalId;
    @Column(name="attempts", nullable=false) public int attempts;
    @Column(name="next_attempt_at") public Instant nextAttemptAt;
    @Column(name="last_error", length=500) public String lastError;
    @Column(name="result", length=8) public String result;
    @Column(name="findings", length=1000) public String findings;
    @Column(name="matched_standard_image_id") public UUID matchedStandardImageId;
    @Column(name="reason_code", length=60) public String reasonCode;
    @Column(name="model_version", length=120) public String modelVersion;
    @Column(name="quality_valid") public Boolean qualityValid;
    @Column(name="quality_score") public Double qualityScore;
    @Column(name="match_compatible") public Boolean matchCompatible;
    @Column(name="match_score") public Double matchScore;
    @Column(name="correlation_id", nullable=false) public UUID correlationId;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="requested_at") public Instant requestedAt;
    @Column(name="reviewed_at") public Instant reviewedAt;
}
