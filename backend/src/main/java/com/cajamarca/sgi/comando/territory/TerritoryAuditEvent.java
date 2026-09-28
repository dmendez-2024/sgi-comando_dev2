package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="territory_audit_event")
public class TerritoryAuditEvent extends BaseEntity {
    @Column(name="entity_type",nullable=false) public String entityType;
    @Column(name="entity_id",nullable=false) public UUID entityId;
    @Column(name="event_type",nullable=false) public String eventType;
    @Column(name="actor_username",nullable=false) public String actorUsername;
    @Column(name="payload_json",columnDefinition="text",nullable=false) public String payloadJson;
    @Column(name="occurred_at",nullable=false) public Instant occurredAt;
}
