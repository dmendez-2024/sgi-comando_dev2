package com.cajamarca.sgi.comando.coordination;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name="coordination_post")
public class CoordinationPost extends BaseEntity {
    @Column(name="company_id", nullable=false) public UUID companyId;
    @Column(nullable=false, length=24) public String code;
    @Column(nullable=false, length=160) public String name;
    @Column(name="post_type", nullable=false, length=24) public String postType;
    @Column(nullable=false, length=16) public String format;
    @Column(nullable=false, length=16) public String rotation;
    @Column(name="shift_start_time", nullable=false) public LocalTime shiftStartTime;
    @Column(name="day_mask", nullable=false) public int dayMask;
    @Column(nullable=false, length=16) public String status;
    @Column(name="updated_by_username", nullable=false, length=80) public String updatedByUsername;
}
