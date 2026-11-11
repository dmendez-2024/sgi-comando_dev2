package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Estado de la sancion de un colaborador dentro de un incidente finalizado. */
@Entity
@Table(name = "incident_sanction_employee")
public class IncidentSanctionEmployeeEntity extends BaseEntity {
    @Column(name = "incident_id", nullable = false) public UUID incidentId;
    @Column(name = "employee_id", nullable = false) public UUID employeeId;
    @Column(name = "persona_id", nullable = false) public Long personaId;
    @Column(name = "dho_sanction_id") public Long dhoSanctionId;
    @Column(name = "decision_status", nullable = false, length = 20) public String decisionStatus;
    @Column(name = "decision_reason", columnDefinition = "text") public String decisionReason;
    @Column(name = "decided_by", length = 160) public String decidedBy;
    @Column(name = "decided_at") public Instant decidedAt;
    @Column(name = "last_event_id") public UUID lastEventId;
    @Column(name = "last_event_hash", length = 64) public String lastEventHash;
}
