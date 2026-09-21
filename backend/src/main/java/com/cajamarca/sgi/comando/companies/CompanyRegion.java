package com.cajamarca.sgi.comando.companies;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="company_region")
public class CompanyRegion extends BaseEntity {
    @Column(name="company_id", nullable=false) public UUID companyId;
    @Column(name="region_id", nullable=false) public UUID regionId;
}
