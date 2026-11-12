package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A classified personnel departure supplied by SIC:RRHH; never inferred locally. */
@Entity
@Table(name = "employee_separation_event")
public class EmployeeSeparationEvent extends BaseEntity {
    @Column(name = "employee_id", nullable = false)
    public UUID employeeId;

    @Column(name = "company_id", nullable = false)
    public UUID companyId;

    @Column(name = "separation_reason", nullable = false, length = 32)
    public String separationReason;

    @Column(name = "effective_at", nullable = false)
    public Instant effectiveAt;

    @Column(name = "source_updated_at", nullable = false)
    public Instant sourceUpdatedAt;

    @Column(name = "source_idempotency_key", nullable = false, length = 160)
    public String sourceIdempotencyKey;
}
