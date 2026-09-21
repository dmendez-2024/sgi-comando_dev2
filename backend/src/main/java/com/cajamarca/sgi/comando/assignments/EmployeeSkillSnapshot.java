package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="employee_skill_snapshot")
public class EmployeeSkillSnapshot extends BaseEntity {
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(name="skill_access", nullable=false) public BigDecimal skillAccess;
    @Column(name="skill_patrol", nullable=false) public BigDecimal skillPatrol;
    @Column(name="skill_observation", nullable=false) public BigDecimal skillObservation;
    @Column(name="skill_tactical", nullable=false) public BigDecimal skillTactical;
    @Column(name="skill_customer", nullable=false) public BigDecimal skillCustomer;
    @Column(name="skill_communication", nullable=false) public BigDecimal skillCommunication;
    @Column(name="skill_discipline", nullable=false) public BigDecimal skillDiscipline;
    @Column(name="skill_response", nullable=false) public BigDecimal skillResponse;
    @Column(name="source_version") public String sourceVersion;

    public double[] values() {
        return new double[]{d(skillAccess),d(skillPatrol),d(skillObservation),d(skillTactical),d(skillCustomer),d(skillCommunication),d(skillDiscipline),d(skillResponse)};
    }
    private static double d(BigDecimal v){ return v==null?0d:v.doubleValue(); }
}
