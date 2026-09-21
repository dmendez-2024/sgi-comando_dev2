package com.cajamarca.sgi.comando.outbox;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="outbox_event")
public class OutboxEvent extends PanacheEntityBase {
 @Id public UUID id;
 @Column(name="instance_country_id",nullable=false) public UUID instanceCountryId;
 @Column(name="aggregate_type",nullable=false) public String aggregateType;
 @Column(name="aggregate_id",nullable=false) public UUID aggregateId;
 @Column(name="event_type",nullable=false) public String eventType;
 @Column(name="payload_json",columnDefinition="text",nullable=false) public String payloadJson;
 @Column(name="created_at",nullable=false) public Instant createdAt;
 @Column(name="published_at") public Instant publishedAt;
 public static OutboxEvent of(UUID tenant,String aggregateType,UUID aggregateId,String type,String payload){
   OutboxEvent e=new OutboxEvent(); e.id=UUID.randomUUID(); e.instanceCountryId=tenant; e.aggregateType=aggregateType; e.aggregateId=aggregateId; e.eventType=type; e.payloadJson=payload; e.createdAt=Instant.now(); return e;
 }
}
