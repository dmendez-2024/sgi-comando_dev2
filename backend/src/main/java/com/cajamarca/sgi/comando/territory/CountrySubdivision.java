package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="country_subdivision")
public class CountrySubdivision extends BaseEntity {
    @Column(name="core_subdivision_id") public UUID coreSubdivisionId;
    @Column(nullable=false) public String code;
    @Column(nullable=false) public String name;
    @Column(name="zone_id") public UUID zoneId;
    @Column(name="region_id") public UUID regionId;
    @Column(nullable=false) public String status;
    @Column(name="core_dataset_version", length=80) public String coreDatasetVersion;
    @Column(name="geometry_json", columnDefinition="text") public String geometryJson;
}
