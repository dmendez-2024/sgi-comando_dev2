package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_notification")
public class IncidentNotificationEntity extends BaseEntity {
    @Column(nullable = false, length = 40) public String code;
    @Column(nullable = false, length = 16) public String status;
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "point_id") public UUID pointId;
    @Column(name = "post_id") public UUID postId;
    @Column(name = "operational_report_id") public UUID operationalReportId;
    @Column(name = "category_catalog_id") public UUID categoryCatalogId;
    @Column(name = "subcategory_catalog_id") public UUID subcategoryCatalogId;
    @Column(name = "incident_type_catalog_id") public UUID incidentTypeCatalogId;
    @Column(name = "severity_catalog_id") public UUID severityCatalogId;
    @Column(name = "created_by", nullable = false, length = 160) public String createdBy;
    @Column(name = "updated_by", nullable = false, length = 160) public String updatedBy;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") public String payloadJson;
    @Column(name = "sanction_delivery_status", nullable = false, length = 20) public String sanctionDeliveryStatus;
    @Column(name = "sanction_event_id") public UUID sanctionEventId;
    @Column(name = "sanction_delivery_attempts", nullable = false) public int sanctionDeliveryAttempts;
    @Column(name = "sanction_next_attempt_at") public Instant sanctionNextAttemptAt;
    @Column(name = "sanction_sent_at") public Instant sanctionSentAt;
    @Column(name = "sanction_last_http_status") public Integer sanctionLastHttpStatus;
    @Column(name = "sanction_last_error", length = 500) public String sanctionLastError;
}
