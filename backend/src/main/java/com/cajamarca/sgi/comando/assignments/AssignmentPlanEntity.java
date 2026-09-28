package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="assignment_plan")
public class AssignmentPlanEntity extends BaseEntity {
    @Column(name="company_id", nullable=false) public UUID companyId;
    @Column(name="week_start", nullable=false) public LocalDate weekStart;
    @Column(nullable=false) public String status;
    @Column(name="published_at") public Instant publishedAt;
    @Column(name="published_by_username") public String publishedByUsername;
    @Column(name="published_snapshot_json", columnDefinition="text") public String publishedSnapshotJson;
    @Column(name="published_required_shifts") public Integer publishedRequiredShifts;
    @Column(name="published_assigned_shifts") public Integer publishedAssignedShifts;
    @Column(name="published_coverage_pct", precision=6, scale=2) public java.math.BigDecimal publishedCoveragePct;
    @Column(name="closed_at") public Instant closedAt;
    @Column(name="draft_saved_at") public Instant draftSavedAt;
    @Column(name="draft_saved_by_username") public String draftSavedByUsername;
}
