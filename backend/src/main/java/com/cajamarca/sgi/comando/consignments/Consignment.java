package com.cajamarca.sgi.comando.consignments;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="consignment")
public class Consignment extends BaseEntity {
 @Column(nullable=false) public String code;
 @Column(nullable=false) public String title;
 @Column(columnDefinition="text",nullable=false) public String instruction;
 @Column(name="point_id",nullable=false) public UUID pointId;
 @Column(name="post_id") public UUID postId;
 @Column(name="protocol_id",nullable=false) public UUID protocolId;
 @Column(nullable=false) public String priority;
 @Column(nullable=false) public String status;
 @Column(name="scope_type",nullable=false) public String scopeType;
 @Column(name="validity_type",nullable=false) public String validityType;
 @Column(name="validity_from") public Instant validityFrom;
 @Column(name="validity_until") public Instant validityUntil;
 @Column(name="application_type",nullable=false) public String applicationType;
 @Column(name="application_days_json",nullable=false,columnDefinition="text") public String applicationDaysJson;
 @Column(name="application_time_from") public LocalTime applicationTimeFrom;
 @Column(name="application_time_to") public LocalTime applicationTimeTo;
 @Column(name="acknowledgment_required",nullable=false) public boolean acknowledgmentRequired;
 @Column(name="confirmation_required",nullable=false) public boolean confirmationRequired;
 @Column(name="evidence_required",nullable=false) public boolean evidenceRequired;
 @Column(name="gps_required",nullable=false) public boolean gpsRequired;
 @Column(name="observation_required",nullable=false) public boolean observationRequired;
 @Column(name="expected_location_mode",nullable=false) public String expectedLocationMode;
 @Column(name="ats_package_id") public UUID atsPackageId;
 @Column(name="ats_x") public Double atsX;
 @Column(name="ats_y") public Double atsY;
 @Column(name="expected_latitude") public Double expectedLatitude;
 @Column(name="expected_longitude") public Double expectedLongitude;
 /** Radio propio (m) de la ubicación esperada; null = predeterminado de la instancia. */
 @Column(name="expected_radius_m") public Integer expectedRadiusM;
 @Column(name="published_at") public Instant publishedAt;
 @Column(name="updated_by_username",nullable=false) public String updatedByUsername;
}
