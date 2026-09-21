package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name="post_shift_template")
public class PostShiftTemplate extends BaseEntity {
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="shift_code", nullable=false) public String shiftCode;
    @Column(name="shift_name", nullable=false) public String shiftName;
    @Column(name="start_time", nullable=false) public LocalTime startTime;
    @Column(name="end_time", nullable=false) public LocalTime endTime;
    @Column(name="day_mask", nullable=false) public int dayMask;
    @Column(nullable=false) public boolean active;
    @Column(name="commercial_version", nullable=false) public String commercialVersion;
}
