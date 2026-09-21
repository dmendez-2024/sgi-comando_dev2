package com.cajamarca.sgi.comando.common;

import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name="instance_country_id", nullable=false)
    public UUID instanceCountryId;

    @Column(name="created_at", nullable=false, updatable=false)
    public Instant createdAt;

    @Column(name="updated_at", nullable=false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
