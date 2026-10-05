package com.cajamarca.sgi.comando.execution;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Foto enviada por el agente. El binario vive en MinIO (bucket + object_key). */
@Entity
@Table(name="evidence_object")
public class EvidenceObject extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name="instance_country_id", nullable=false) public UUID instanceCountryId;
    @Column(name="client_evidence_id", nullable=false) public UUID clientEvidenceId;
    @Column(name="upload_batch_id", nullable=false) public UUID uploadBatchId;
    @Column(name="event_id", nullable=false) public UUID eventId;
    @Column(name="assignment_id", nullable=false) public UUID assignmentId;
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(name="username", nullable=false, length=80) public String username;
    @Column(name="target_type", nullable=false, length=40) public String targetType;
    @Column(name="target_id", nullable=false) public UUID targetId;
    @Column(name="bucket", nullable=false, length=63) public String bucket;
    @Column(name="object_key", nullable=false, length=300) public String objectKey;
    @Column(name="content_type", nullable=false, length=40) public String contentType;
    @Column(name="size_bytes", nullable=false) public long sizeBytes;
    @Column(name="sha256", nullable=false, length=64) public String sha256;
    @Column(name="captured_at", nullable=false) public Instant capturedAt;
    @Column(name="latitude") public Double latitude;
    @Column(name="longitude") public Double longitude;
    @Column(name="accuracy_m") public Double accuracyM;
    @Column(name="source", nullable=false, length=16) public String source;
    @Column(name="flags", nullable=false, length=200) public String flags = "";
    /** Radio (m) y distancia (m) a la referencia con que se evaluó la foto al recibirla; null si no hubo referencia o GPS. */
    @Column(name="reference_radius_m") public Integer referenceRadiusM;
    @Column(name="reference_distance_m") public Integer referenceDistanceM;
    @Column(name="status", nullable=false, length=16) public String status;
    @Column(name="received_at", nullable=false) public Instant receivedAt;

    public static EvidenceObject byClientId(UUID tenant, UUID clientEvidenceId) {
        return find("instanceCountryId=?1 and clientEvidenceId=?2", tenant, clientEvidenceId).firstResult();
    }
}
