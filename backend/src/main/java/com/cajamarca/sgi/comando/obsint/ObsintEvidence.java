package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fotograma capturado de una cámara como respaldo de la respuesta. El archivo vive en MinIO (bucket de evidencias). */
@Entity
@Table(name = "obsint_evidence")
public class ObsintEvidence extends BaseEntity {
    @Column(name = "task_id", nullable = false) public UUID taskId;
    @Column(name = "camera_id", nullable = false) public UUID cameraId;
    @Column(name = "video_time_seconds", nullable = false) public double videoTimeSeconds;
    @Column(name = "captured_at", nullable = false) public Instant capturedAt;
    @Column(nullable = false, length = 120) public String bucket;
    @Column(name = "object_key", nullable = false, length = 400) public String objectKey;
    @Column(name = "content_type", nullable = false, length = 100) public String contentType;
    @Column(name = "size_bytes", nullable = false) public long sizeBytes;
    @Column(nullable = false, length = 64) public String sha256;
}
