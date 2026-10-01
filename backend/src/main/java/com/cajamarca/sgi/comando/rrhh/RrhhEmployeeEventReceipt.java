package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persistent idempotency/audit receipt for SIC:RRHH -> SGI:Comando employee events.
 * The key is scoped by Instancia PE (`instance_country_id`).
 */
@Entity
@Table(name = "sic_rrhh_employee_event_receipt")
public class RrhhEmployeeEventReceipt extends BaseEntity {
    @Column(name = "idempotency_key", nullable = false, length = 160)
    public String idempotencyKey;

    @Column(name = "content_hash", nullable = false, length = 64)
    public String contentHash;

    @Column(name = "source_updated_at", nullable = false)
    public Instant sourceUpdatedAt;

    @Column(name = "processing_status", nullable = false, length = 32)
    public String processingStatus;

    @Column(name = "processed_at", nullable = false)
    public Instant processedAt;
}
