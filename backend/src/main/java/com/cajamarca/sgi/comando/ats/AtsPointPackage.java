package com.cajamarca.sgi.comando.ats;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="ats_point_package", uniqueConstraints={
    @UniqueConstraint(name="uq_ats_point_package_revision", columnNames={"point_id","revision_no"})
})
public class AtsPointPackage extends BaseEntity {
    @Column(name="point_id", nullable=false) public UUID pointId;
    @Column(name="revision_no", nullable=false) public int revisionNo;
    @Column(name="is_current", nullable=false) public boolean current;

    @Column(name="original_filename", nullable=false, length=255) public String originalFilename;
    @Column(name="ats_schema_version", length=32) public String atsSchemaVersion;
    @Column(name="package_type", length=96) public String packageType;

    @Column(name="source_pto_id", length=80) public String sourcePtoId;
    @Column(name="source_pto_code", length=80) public String sourcePtoCode;
    @Column(name="source_pto_name", length=255) public String sourcePtoName;
    @Column(name="model_revision") public Integer modelRevision;
    @Column(name="model_hash", length=128) public String modelHash;

    @Column(name="publication_id", length=120) public String publicationId;
    @Column(name="publication_version", length=64) public String publicationVersion;
    @Column(name="publication_status", length=64) public String publicationStatus;
    @Column(name="published_at") public Instant publishedAt;
    @Column(name="package_hash", length=128) public String packageHash;
    @Column(name="archive_sha256", nullable=false, length=128) public String archiveSha256;
    @Column(name="risk_index") public Double riskIndex;

    @Column(name="plan_level_id", length=120) public String planLevelId;
    @Column(name="plan_level_code", length=80) public String planLevelCode;
    @Column(name="plan_level_name", length=255) public String planLevelName;
    @Column(name="plan_original_filename", length=255) public String planOriginalFilename;
    @Column(name="plan_internal_path", nullable=false, length=512) public String planInternalPath;
    @Column(name="plan_content_type", nullable=false, length=80) public String planContentType;
    @Column(name="plan_width_px") public Integer planWidthPx;
    @Column(name="plan_height_px") public Integer planHeightPx;
    @Column(name="plan_sha256", length=128) public String planSha256;

    @Basic(fetch=FetchType.LAZY)
    @Column(name="ats_file", nullable=false, columnDefinition="bytea") public byte[] atsFile;

    @Basic(fetch=FetchType.LAZY)
    @Column(name="plan_image", nullable=false, columnDefinition="bytea") public byte[] planImage;

    @Column(name="imported_by_username", nullable=false, length=80) public String importedByUsername;
}
