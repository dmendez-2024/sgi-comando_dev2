package com.cajamarca.sgi.comando.companies;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="company_version")
public class CompanyVersion extends BaseEntity {
    @Column(name="company_id", nullable=false) public UUID companyId;
    @Column(name="version_number", nullable=false) public int versionNumber;
    @Column(name="change_type", nullable=false) public String changeType;
    @Column(name="change_reason", length=500) public String changeReason;
    @Column(name="actor_username", nullable=false) public String actorUsername;
    @Column(name="effective_at", nullable=false) public Instant effectiveAt;
    @Column(name="snapshot_json", nullable=false, columnDefinition="text") public String snapshotJson;
}
