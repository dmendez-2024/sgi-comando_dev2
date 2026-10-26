package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Cámara que ve el monitorista. streamUrl debe ser del mismo origen que la web para poder capturar fotogramas. */
@Entity
@Table(name = "obsint_camera")
public class ObsintCamera extends BaseEntity {
    @Column(nullable = false, length = 40) public String code;
    @Column(nullable = false, length = 120) public String name;
    @Column(length = 200) public String location;
    @Column(name = "stream_url", nullable = false, length = 500) public String streamUrl;
    /** true: transmisión en vivo; false: grabación que se repite (demo/UAT). */
    @Column(nullable = false) public boolean live;
    @Column(nullable = false) public boolean active = true;
}
