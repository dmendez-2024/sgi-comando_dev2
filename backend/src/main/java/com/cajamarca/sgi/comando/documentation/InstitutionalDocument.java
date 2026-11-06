package com.cajamarca.sgi.comando.documentation;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="institutional_document")
public class InstitutionalDocument extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name="instance_country_id",nullable=false) public UUID instanceCountryId;
    @Column(name="company_id") public UUID companyId;
    @Column(nullable=false,length=180) public String name;
    @Column(nullable=false,length=40) public String version;
    @Column(nullable=false,length=24) public String status;
    @Column(name="original_name",nullable=false,length=255) public String originalName;
    @Column(name="content_type",nullable=false,length=100) public String contentType;
    @Column(nullable=false,length=80) public String bucket;
    @Column(name="object_key",nullable=false,length=400) public String objectKey;
    @Column(nullable=false,length=64) public String sha256;
    @Column(name="size_bytes",nullable=false) public long sizeBytes;
    @Column(name="uploaded_by",nullable=false,length=160) public String uploadedBy;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="updated_at",nullable=false) public Instant updatedAt;
}
