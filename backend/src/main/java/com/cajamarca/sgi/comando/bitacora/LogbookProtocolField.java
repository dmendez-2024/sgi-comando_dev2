package com.cajamarca.sgi.comando.bitacora;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="logbook_protocol_field")
public class LogbookProtocolField extends BaseEntity {
    @Column(name="protocol_id", nullable=false) public UUID protocolId;
    @Column(name="accreditation_id", nullable=false) public UUID accreditationId;
    @Column(name="section", nullable=false, length=24) public String section;
    @Column(name="sort_order", nullable=false) public int sortOrder;
    @Column(name="name", nullable=false, length=160) public String name;
    @Column(name="description", nullable=false, length=1000) public String description;
    @Column(name="field_type", nullable=false, length=24) public String fieldType;
    @Column(name="required", nullable=false) public boolean required;
    @Column(name="evidence_required", nullable=false) public boolean evidenceRequired;
    @Column(name="capture_mode", nullable=false, length=32) public String captureMode;
    @Column(name="custom_field", nullable=false) public boolean customField;

    @Column(name="standard_image_original_name", length=255) public String standardImageOriginalName;
    @Column(name="standard_image_content_type", length=100) public String standardImageContentType;
    @Column(name="standard_image_data", columnDefinition="bytea") public byte[] standardImageData;
    @Column(name="standard_image_object_key", length=300) public String standardImageObjectKey;
    @Column(name="standard_image_sha256", length=64) public String standardImageSha256;
    @Column(name="standard_image_size") public Long standardImageSize;
    @Column(name="standard_image_version", nullable=false) public int standardImageVersion;
    @Column(name="standard_image_notes", length=1000) public String standardImageNotes;
    @Column(name="visint_enabled", nullable=false) public boolean visintEnabled;
}
