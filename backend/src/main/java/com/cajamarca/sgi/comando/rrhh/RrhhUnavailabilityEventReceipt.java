package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Persistent idempotency receipt for SIC:DHO permission/vacation events. */
@Entity
@Table(name = "sic_dho_unavailability_event_receipt")
public class RrhhUnavailabilityEventReceipt extends BaseEntity {
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
