package com.cajamarca.sgi.comando.coordination;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="supervision_route")
public class SupervisionRoute extends BaseEntity {
    @Column(name="series_id", nullable=false) public UUID seriesId;
    @Column(name="based_on_route_id") public UUID basedOnRouteId;
    @Column(name="coordination_post_id", nullable=false) public UUID coordinationPostId;
    @Column(nullable=false, length=32) public String code;
    @Column(nullable=false, length=160) public String name;
    @Column(name="version_no", nullable=false) public int versionNo;
    @Column(nullable=false, length=16) public String status;
    @Column(name="published_at") public Instant publishedAt;
    @Column(name="updated_by_username", nullable=false, length=80) public String updatedByUsername;
}
