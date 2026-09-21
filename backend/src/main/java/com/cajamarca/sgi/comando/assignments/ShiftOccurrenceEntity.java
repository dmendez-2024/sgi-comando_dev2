package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="shift_occurrence")
public class ShiftOccurrenceEntity extends BaseEntity {
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="starts_at", nullable=false) public Instant startsAt;
    @Column(name="ends_at", nullable=false) public Instant endsAt;
    @Column(name="commercial_version") public String commercialVersion;
    @Column(nullable=false) public boolean required;
    @Column(name="template_id") public UUID templateId;
    @Column(name="local_date") public LocalDate localDate;
    @Column(name="shift_code") public String shiftCode;
    @Column(name="shift_name") public String shiftName;
}
