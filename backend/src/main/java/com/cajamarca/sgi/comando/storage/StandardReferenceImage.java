package com.cajamarca.sgi.comando.storage;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/**
 * Foto estándar (1..5, en orden) de un Hito de patrulla, una evidencia de Consigna o un campo de Bitácora.
 * Se guarda en MinIO; VISINT las recibe todas en referenceImages.
 */
@Entity
@Table(name="standard_reference_image")
public class StandardReferenceImage extends PanacheEntityBase {
    public static final int MAX = 5;
    public static final String PATROL_CHECKPOINT = "PATROL_CHECKPOINT", CONSIGNMENT_EVIDENCE = "CONSIGNMENT_EVIDENCE", LOGBOOK_FIELD = "LOGBOOK_FIELD";

    @Id public UUID id;
    @Column(name="instance_country_id", nullable=false) public UUID instanceCountryId;
    @Column(name="target_type", nullable=false, length=40) public String targetType;
    @Column(name="target_id", nullable=false) public UUID targetId;
    @Column(name="position", nullable=false) public int position;
    @Column(name="original_name", length=255) public String originalName;
    @Column(name="content_type", nullable=false, length=100) public String contentType;
    @Column(name="object_key", nullable=false, length=300) public String objectKey;
    @Column(name="sha256", nullable=false, length=64) public String sha256;
    @Column(name="size_bytes", nullable=false) public long sizeBytes;
    @Column(name="created_at", nullable=false) public Instant createdAt;

    public static List<StandardReferenceImage> of(String targetType, UUID targetId) {
        return list("targetType=?1 and targetId=?2 order by position", targetType, targetId);
    }
    public static long countOf(String targetType, UUID targetId) { return count("targetType=?1 and targetId=?2", targetType, targetId); }
}
