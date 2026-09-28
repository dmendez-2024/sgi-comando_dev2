package com.cajamarca.sgi.comando.siccom;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name="sic_com_commercial_event_receipt")
public class SicComCommercialEventReceipt extends BaseEntity {
  @Column(name="event_id", nullable=false, length=160) public String eventId;
  @Column(name="content_hash", nullable=false, length=64) public String contentHash;
  @Column(name="commercial_version", nullable=false, length=80) public String commercialVersion;
  @Column(name="processing_status", nullable=false, length=32) public String processingStatus;
  @Column(name="response_json", nullable=false, columnDefinition="text") public String responseJson;
  @Column(name="occurred_at", nullable=false) public Instant occurredAt;
  @Column(name="processed_at", nullable=false) public Instant processedAt;
}
