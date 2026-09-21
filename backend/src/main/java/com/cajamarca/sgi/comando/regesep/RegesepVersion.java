package com.cajamarca.sgi.comando.regesep;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="regesep_version")
public class RegesepVersion extends BaseEntity {
 @Column(name="point_id",nullable=false) public UUID pointId;
 @Column(nullable=false) public int version;
 @Column(nullable=false) public String code;
 @Column(nullable=false) public String status;
 @Column(name="effective_at",nullable=false) public Instant effectiveAt;
 @Column(name="trigger_type",nullable=false) public String triggerType;
 @Column(name="source_manifest_json",columnDefinition="text",nullable=false) public String sourceManifestJson;
}
