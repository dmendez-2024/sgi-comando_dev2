package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="post_planning_cycle_snapshot")
public class PostPlanningCycleSnapshot extends BaseEntity {
    @Column(name="post_id", nullable=false, unique=true) public UUID postId;
    @Column(name="rotation_code", nullable=false, length=32) public String rotationCode;
    @Column(name="cycle_length_days", nullable=false) public Integer cycleLengthDays;
    @Column(name="source_system", nullable=false, length=32) public String sourceSystem;
    @Column(name="source_version", nullable=false, length=80) public String sourceVersion;
}
