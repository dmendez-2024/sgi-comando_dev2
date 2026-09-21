package com.cajamarca.sgi.comando.coordination;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="supervision_route_point")
public class SupervisionRoutePoint extends BaseEntity {
    @Column(name="route_id", nullable=false) public UUID routeId;
    @Column(name="point_id", nullable=false) public UUID pointId;
    @Column(name="sort_order", nullable=false) public int sortOrder;
    @Column(name="point_code_snapshot", nullable=false, length=80) public String pointCodeSnapshot;
    @Column(name="point_name_snapshot", nullable=false, length=180) public String pointNameSnapshot;
}
