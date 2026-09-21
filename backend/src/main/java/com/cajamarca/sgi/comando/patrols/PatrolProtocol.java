package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="patrol_protocol")
public class PatrolProtocol extends BaseEntity {
    @Column(name="series_id", nullable=false) public UUID seriesId;
    @Column(name="based_on_protocol_id") public UUID basedOnProtocolId;
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="code", nullable=false, length=32) public String code;
    @Column(name="name", nullable=false, length=180) public String name;
    @Column(name="description", nullable=false, length=1000) public String description;
    @Column(name="status", nullable=false, length=24) public String status;
    @Column(name="version_no", nullable=false) public int versionNo;
    @Column(name="last_published_at") public Instant lastPublishedAt;
    @Column(name="updated_by_username", nullable=false, length=80) public String updatedByUsername;
}
