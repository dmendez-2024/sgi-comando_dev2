package com.cajamarca.sgi.comando.patrols;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** Foto estándar de un Hito (hasta 5, en orden). Se guarda en MinIO; VISINT la recibe en referenceImages. */
@Entity
@Table(name="patrol_checkpoint_standard_image")
public class PatrolCheckpointStandardImage extends PanacheEntityBase {
    public static final int MAX = 5;

    @Id public UUID id;
    @Column(name="instance_country_id", nullable=false) public UUID instanceCountryId;
    @Column(name="checkpoint_id", nullable=false) public UUID checkpointId;
    @Column(name="position", nullable=false) public int position;
    @Column(name="original_name", length=255) public String originalName;
    @Column(name="content_type", nullable=false, length=100) public String contentType;
    @Column(name="object_key", nullable=false, length=300) public String objectKey;
    @Column(name="sha256", nullable=false, length=64) public String sha256;
    @Column(name="size_bytes", nullable=false) public long sizeBytes;
    @Column(name="created_at", nullable=false) public Instant createdAt;

    public static List<PatrolCheckpointStandardImage> of(UUID checkpointId) {
        return list("checkpointId=?1 order by position", checkpointId);
    }
}
