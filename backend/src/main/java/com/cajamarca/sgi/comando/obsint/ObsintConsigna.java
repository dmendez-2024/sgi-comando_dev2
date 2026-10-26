package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Consigna OBSINT: lo que el monitorista debe verificar, en cuánto tiempo y sobre qué cámaras.
 * Es el origen de cada tarea del caso; la tarea copia instrucción y tiempo para que cambiar la consigna no altere casos ya creados.
 */
@Entity
@Table(name = "obsint_consigna")
public class ObsintConsigna extends BaseEntity {
    @Column(nullable = false, length = 40) public String code;
    @Column(nullable = false, length = 160) public String title;
    @Column(nullable = false, length = 1000) public String instruction;
    @Column(name = "time_limit_seconds", nullable = false) public int timeLimitSeconds;
    @Column(nullable = false) public int position;
    @Column(nullable = false) public boolean active = true;

    @ManyToMany
    @JoinTable(name = "obsint_consigna_camera", joinColumns = @JoinColumn(name = "consigna_id"), inverseJoinColumns = @JoinColumn(name = "camera_id"))
    @OrderColumn(name = "position")
    public List<ObsintCamera> cameras = new ArrayList<>();
}
