package com.cajamarca.sgi.comando.context;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name="core_instance_country_snapshot")
public class CoreInstanceCountrySnapshot extends BaseEntity {
    @Column(name="core_instance_country_id") public java.util.UUID coreInstanceCountryId;
    @Column(name="country_code", nullable=false, length=8) public String countryCode;
    @Column(name="country_name", nullable=false, length=160) public String countryName;
    @Column(nullable=false, length=24) public String locale;
    @Column(nullable=false, length=64) public String timezone;
    @Column(nullable=false, length=8) public String currency;
    @Column(name="subdivision_type", nullable=false, length=32) public String subdivisionType;
    @Column(name="subdivision_singular", nullable=false, length=64) public String subdivisionSingular;
    @Column(name="subdivision_plural", nullable=false, length=64) public String subdivisionPlural;
    @Column(name="territorial_dataset_version", nullable=false, length=80) public String territorialDatasetVersion;
    @Column(name="synced_at", nullable=false) public Instant syncedAt;
}
