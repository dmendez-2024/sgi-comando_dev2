package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="company_membership")
public class CompanyMembershipEntity extends BaseEntity {
    @Column(name="company_id", nullable=false) public UUID companyId;
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(name="membership_type", nullable=false, length=32) public String membershipType;
    @Column(name="role_code", nullable=false, length=80) public String roleCode;
    @Column(name="starts_at", nullable=false) public Instant startsAt;
    @Column(name="ends_at") public Instant endsAt;
    @Column(name="required_change", nullable=false) public boolean requiredChange;
}
