package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="country_subdivision")
public class CountrySubdivision extends BaseEntity {
    @Column(name="core_subdivision_id") public UUID coreSubdivisionId;
    @Column(nullable=false, length=16) public String code;
    @Column(name="official_code", length=32) public String officialCode;
    @Column(nullable=false, length=120) public String name;
    @Column(name="subdivision_type", length=32) public String subdivisionType;
    @Column(name="source_version", length=80) public String sourceVersion;
    @Column(name="zone_id") public UUID zoneId;
    @Column(name="region_id") public UUID regionId;
    @Column(nullable=false) public String status;
}
