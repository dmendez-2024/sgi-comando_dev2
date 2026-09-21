package com.cajamarca.sgi.comando.companies;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="core_company_catalog_snapshot")
public class CoreCompanyCatalogSnapshot extends BaseEntity {
    @Column(name="core_company_id", nullable=false) public UUID coreCompanyId;
    @Column(nullable=false, length=32) public String code;
    @Column(nullable=false, length=160) public String name;
    @Column(name="historical_review", length=750) public String historicalReview;
    @Column(name="logo_data_url", columnDefinition="text") public String logoDataUrl;
    @Column(name="company_type", nullable=false, length=32) public String companyType;
    @Column(name="source_version", nullable=false, length=80) public String sourceVersion;
    @Column(name="source_status", nullable=false, length=32) public String sourceStatus;
    @Column(name="synced_at", nullable=false) public Instant syncedAt;
}
