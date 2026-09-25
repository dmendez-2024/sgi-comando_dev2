package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="country_subdivision")
public class CountrySubdivision extends BaseEntity {
    @Column(nullable=false) public String code;
    @Column(nullable=false) public String name;
    @Column(name="zone_id") public UUID zoneId;
    @Column(name="region_id") public UUID regionId;
    @Column(nullable=false) public String status;
}
