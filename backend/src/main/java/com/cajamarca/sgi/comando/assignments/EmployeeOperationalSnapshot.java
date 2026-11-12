package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="employee_operational_snapshot")
public class EmployeeOperationalSnapshot extends BaseEntity {
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(name="persona_id") public Long personaId;
    @Column(name="company_id", nullable=false) public UUID companyId;
    @Column(name="full_name", nullable=false) public String fullName;
    @Column(name="identification_number", length=80) public String identificationNumber;
    @Column(name="corporate_email", length=254) public String corporateEmail;
    @Column(name="role_code", nullable=false) public String roleCode;
    @Column(name="employment_status", nullable=false) public String employmentStatus;
    @Column(name="id_score", precision=5, scale=2) public BigDecimal idScore;
    @Column(name="preferred_shift") public String preferredShift;
    @Column(name="required_change", nullable=false) public boolean requiredChange;
    @Column(name="photo_key") public String photoKey;
    @Column(name="photo_bucket", length=63) public String photoBucket;
    @Column(name="photo_content_type", length=40) public String photoContentType;
    @Column(name="photo_sha256", length=64) public String photoSha256;
    @Column(name="photo_size_bytes") public Long photoSizeBytes;
    @Column(name="updated_from_source_at", nullable=false) public Instant updatedFromSourceAt;
}
