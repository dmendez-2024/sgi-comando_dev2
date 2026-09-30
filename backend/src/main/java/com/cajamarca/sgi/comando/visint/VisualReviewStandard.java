package com.cajamarca.sgi.comando.visint;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.*;

/** Foto estándar enviada a VISINT en una revisión (snapshot: si después cambian las del Hito, se conserva la enviada). */
@Entity
@Table(name="visual_review_standard")
public class VisualReviewStandard extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name="review_id", nullable=false) public UUID reviewId;
    @Column(name="position", nullable=false) public int position;
    @Column(name="standard_image_id", nullable=false) public UUID standardImageId;
    @Column(name="bucket", nullable=false, length=63) public String bucket;
    @Column(name="object_key", nullable=false, length=300) public String objectKey;
    @Column(name="sha256", nullable=false, length=64) public String sha256;
    @Column(name="content_type", nullable=false, length=100) public String contentType;

    public static List<VisualReviewStandard> of(UUID reviewId) { return list("reviewId=?1 order by position", reviewId); }
}
