package com.cajamarca.sgi.comando.companies;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="company")
public class Company extends BaseEntity {
    @Column(nullable=false, unique=false) public String code;
    @Column(nullable=false) public String name;
    @Column(nullable=false) public String status;
    @Column(name="required_change_count", nullable=false) public int requiredChangeCount;

    /**
     * Compatibilidad con TER v1.0 y verticales aún no migradas a multi-región.
     * COM v0.1 usa company_region como relación territorial autoritativa y mantiene
     * region_id apuntando a una de las regiones actuales para no alterar otras verticales.
     */
    @Column(name="region_id") public UUID regionId;

    @Column(name="zone_id") public UUID zoneId;
    @Column(name="historical_review", length=750) public String historicalReview;
    @Column(name="logo_data_url", columnDefinition="text") public String logoDataUrl;
    @Column(name="version_number", nullable=false) public int versionNumber;

    @Column(name="core_catalog_id") public UUID coreCatalogId;
    @Column(name="source_system", nullable=false, length=32) public String sourceSystem;
    @Column(name="source_version", length=80) public String sourceVersion;
    @Column(name="company_type", nullable=false, length=32) public String companyType;
    @Column(name="always_active", nullable=false) public boolean alwaysActive;
}
