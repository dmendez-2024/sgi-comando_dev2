package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "incident_notification")
public class IncidentNotificationEntity extends BaseEntity {
    @Column(nullable = false, length = 40) public String code;
    @Column(nullable = false, length = 16) public String status;
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "point_id") public UUID pointId;
    @Column(name = "post_id") public UUID postId;
    @Column(name = "created_by", nullable = false, length = 160) public String createdBy;
    @Column(name = "updated_by", nullable = false, length = 160) public String updatedBy;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") public String payloadJson;
}
