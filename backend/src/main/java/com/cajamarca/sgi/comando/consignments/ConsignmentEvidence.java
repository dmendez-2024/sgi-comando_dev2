package com.cajamarca.sgi.comando.consignments;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="consignment_evidence")
public class ConsignmentEvidence extends BaseEntity {
 @Column(name="consignment_id",nullable=false) public UUID consignmentId;
 @Column(name="sort_order",nullable=false) public int sortOrder;
 @Column(nullable=false,length=180) public String name;
 @Column(nullable=false,length=1000) public String description;
 @Column(name="evidence_type",nullable=false,length=32) public String evidenceType;
 @Column(nullable=false) public boolean required;
 @Column(name="standard_image_original_name",length=255) public String standardImageOriginalName;
 @Column(name="standard_image_content_type",length=100) public String standardImageContentType;
 @Basic(fetch=FetchType.LAZY) @Column(name="standard_image_data",columnDefinition="bytea") public byte[] standardImageData;
 @Column(name="standard_image_version",nullable=false) public int standardImageVersion;
 @Column(name="standard_image_notes",nullable=false,length=1000) public String standardImageNotes;
 @Column(name="visint_enabled",nullable=false) public boolean visintEnabled;
}
