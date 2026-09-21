package com.cajamarca.sgi.comando.postconfig;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="post_operational_config", uniqueConstraints={@UniqueConstraint(name="uq_post_operational_config_post", columnNames={"post_id"})})
public class PostOperationalConfig extends BaseEntity {
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="post_type", nullable=false, length=8) public String postType;
    @Column(name="description", nullable=false, length=600) public String description;
    @Column(name="ats_location_key", nullable=false, length=64) public String atsLocationKey;
    @Column(name="ats_location_label", nullable=false, length=160) public String atsLocationLabel;
    @Column(name="ats_package_id") public UUID atsPackageId;
    @Column(name="ats_location_x") public Double atsLocationX;
    @Column(name="ats_location_y") public Double atsLocationY;
    @Column(name="skill_attendance", nullable=false) public int skillAttendance;
    @Column(name="skill_access_control", nullable=false) public int skillAccessControl;
    @Column(name="skill_patrol", nullable=false) public int skillPatrol;
    @Column(name="skill_judgement", nullable=false) public int skillJudgement;
    @Column(name="skill_tactical", nullable=false) public int skillTactical;
    @Column(name="skill_bearing", nullable=false) public int skillBearing;
    @Column(name="skill_leadership", nullable=false) public int skillLeadership;
    @Column(name="skill_customer_service", nullable=false) public int skillCustomerService;
    @Column(name="adjustment_justification", length=600) public String adjustmentJustification;
    @Column(name="config_status", nullable=false, length=24) public String configStatus;
    @Column(name="updated_by_username", nullable=false, length=80) public String updatedByUsername;
}
