package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="assignment_event")
public class AssignmentEventEntity extends BaseEntity {
    @Column(name="assignment_plan_id", nullable=false) public UUID assignmentPlanId;
    @Column(name="assignment_id") public UUID assignmentId;
    @Column(name="shift_occurrence_id") public UUID shiftOccurrenceId;
    @Column(name="event_type", nullable=false) public String eventType;
    @Column(name="original_employee_id") public UUID originalEmployeeId;
    @Column(name="new_employee_id") public UUID newEmployeeId;
    @Column(name="actor_username", nullable=false) public String actorUsername;
    @Column(columnDefinition="text") public String reason;
    @Column(name="payload_json", columnDefinition="text", nullable=false) public String payloadJson;
    @Column(name="occurred_at", nullable=false) public Instant occurredAt;
}
