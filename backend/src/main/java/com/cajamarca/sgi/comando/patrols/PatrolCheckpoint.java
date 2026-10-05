package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Hito canónico de patrol_definition. */
@Entity
@Table(name="patrol_checkpoint")
public class PatrolCheckpoint extends BaseEntity {
    @Column(name="patrol_definition_id", nullable=false) public UUID patrolId;
    @Column(name="sequence_no", nullable=false) public int sortOrder;
    @Column(name="name", nullable=false, length=180) public String name;

    // Compatibilidad baseline. Las reglas normalizadas viven en patrol_checkpoint_rule.
    @Column(name="radius_m") public Integer radiusM;
    @Column(name="validation_rule_json", nullable=false, columnDefinition="text") public String validationRuleJson;

    // Campos SER.
    @Column(name="code", nullable=false, length=32) public String code;
    @Column(name="description", nullable=false, length=1000) public String description;
    @Column(name="origin_mode", nullable=false, length=16) public String originMode;
    @Column(name="ats_package_id") public UUID atsPackageId;
    @Column(name="ats_x") public Double atsX;
    @Column(name="ats_y") public Double atsY;
    @Column(name="latitude") public Double latitude;
    @Column(name="longitude") public Double longitude;
    @Column(name="gps_accuracy_m") public Double gpsAccuracyM;
    @Column(name="location_captured_at") public Instant locationCapturedAt;
    @Column(name="location_captured_by", length=80) public String locationCapturedBy;
    @Column(name="control_type", nullable=false, length=40) public String controlType;
    @Column(name="requires_evidence", nullable=false) public boolean requiresEvidence;
    @Column(name="standard_image_original_name", length=255) public String standardImageOriginalName;
    @Column(name="standard_image_content_type", length=100) public String standardImageContentType;
    @Basic(fetch=FetchType.LAZY)
    @Column(name="standard_image_data", columnDefinition="bytea") public byte[] standardImageData;
    @Column(name="standard_image_object_key", length=300) public String standardImageObjectKey;
    @Column(name="standard_image_sha256", length=64) public String standardImageSha256;
    @Column(name="standard_image_size") public Long standardImageSize;
    @Column(name="standard_image_version", nullable=false) public int standardImageVersion;
    @Column(name="standard_image_notes", nullable=false, length=1000) public String standardImageNotes;
    @Column(name="visint_enabled", nullable=false) public boolean visintEnabled;
    /** Umbral de coincidencia de VISINT (0.00 a 1.00); null = predeterminado. */
    @Column(name="match_threshold") public Double matchThreshold;
}
