package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="patrol_checkpoint_rule")
public class PatrolCheckpointRule extends BaseEntity {
    @Column(name="checkpoint_id", nullable=false) public UUID checkpointId;
    @Column(name="sort_order", nullable=false) public int sortOrder;
    @Column(name="rule_type", nullable=false, length=40) public String ruleType;
    @Column(name="required", nullable=false) public boolean required;
    @Column(name="evidence_required", nullable=false) public boolean evidenceRequired;
}
