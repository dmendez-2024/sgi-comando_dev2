package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="territory_zone")
public class TerritoryZone extends BaseEntity {
    @Column(nullable=false) public String code;
    @Column(nullable=false) public String name;
    @Column(nullable=false) public String status;
    @Column(name="responsible_employee_id") public UUID responsibleEmployeeId;
}
