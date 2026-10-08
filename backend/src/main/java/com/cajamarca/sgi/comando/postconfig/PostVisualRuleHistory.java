package com.cajamarca.sgi.comando.postconfig;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="post_visual_rule_history")
public class PostVisualRuleHistory extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name="instance_country_id", nullable=false) public UUID instanceCountryId;
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="threshold_percent") public Integer thresholdPercent;
    @Column(name="threshold_value") public Double thresholdValue;
    @Column(name="reference_image_count") public Integer referenceImageCount;
    @Column(name="historical_presentation", length=1000) public String historicalPresentation;
    @Column(name="historical_presentation_months") public Integer historicalPresentationMonths;
    @Column(name="changed_by_username", nullable=false, length=80) public String changedByUsername;
    @Column(name="changed_at", nullable=false) public Instant changedAt;
}
