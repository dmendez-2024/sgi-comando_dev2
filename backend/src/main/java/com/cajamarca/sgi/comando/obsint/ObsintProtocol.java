package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Protocolo OBSINT de un Punto, versionado como Consignas: BORRADOR (editable) → publicar → INACTIVO (inmutable) → activar → ACTIVO.
 * Solo un ACTIVO y un BORRADOR por Punto. La Consola usa las consignas del protocolo ACTIVO.
 */
@Entity
@Table(name = "obsint_protocol")
public class ObsintProtocol extends BaseEntity {
    @Column(name = "series_id", nullable = false) public UUID seriesId;
    @Column(name = "based_on_protocol_id") public UUID basedOnProtocolId;
    @Column(name = "point_id", nullable = false) public UUID pointId;
    @Column(nullable = false, length = 32) public String code;
    @Column(name = "version_no", nullable = false) public int versionNo;
    @Column(nullable = false, length = 16) public String status;
    @Column(name = "published_at") public Instant publishedAt;
    @Column(name = "activated_at") public Instant activatedAt;
    @Column(name = "updated_by", nullable = false, length = 160) public String updatedBy;
}
