package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="consignment_protocol")
public class ConsignmentProtocol extends BaseEntity {
 @Column(name="series_id",nullable=false) public UUID seriesId;
 @Column(name="based_on_protocol_id") public UUID basedOnProtocolId;
 @Column(name="point_id",nullable=false) public UUID pointId;
 @Column(nullable=false,length=32) public String code;
 @Column(nullable=false,length=180) public String name;
 @Column(nullable=false,length=1000) public String description;
 @Column(nullable=false,length=24) public String status;
 @Column(name="version_no",nullable=false) public int versionNo;
 @Column(name="published_at") public Instant publishedAt;
 @Column(name="activated_at") public Instant activatedAt;
 @Column(name="updated_by_username",nullable=false,length=80) public String updatedByUsername;
}
