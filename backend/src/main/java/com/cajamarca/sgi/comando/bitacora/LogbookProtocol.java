package com.cajamarca.sgi.comando.bitacora;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="logbook_protocol")
public class LogbookProtocol extends BaseEntity {
    @Column(name="series_id", nullable=false) public UUID seriesId;
    @Column(name="based_on_protocol_id") public UUID basedOnProtocolId;
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="code", nullable=false, length=32) public String code;
    @Column(name="name", nullable=false, length=180) public String name;
    @Column(name="object_type", nullable=false, length=16) public String objectType;
    @Column(name="application_type", nullable=false, length=16) public String applicationType;
    @Column(name="description", nullable=false, length=1000) public String description;
    @Column(name="status", nullable=false, length=24) public String status;
    @Column(name="version_no", nullable=false) public int versionNo;
    @Column(name="identification_logic", nullable=false, length=8) public String identificationLogic;
    @Column(name="verification_logic", nullable=false, length=8) public String verificationLogic;

    @Column(name="auth_preapproval", nullable=false) public boolean authPreapproval;
    @Column(name="auth_client", nullable=false) public boolean authClient;
    @Column(name="auth_supervisor", nullable=false) public boolean authSupervisor;
    @Column(name="white_list_enabled", nullable=false) public boolean whiteListEnabled;
    @Column(name="black_list_enabled", nullable=false) public boolean blackListEnabled;

    @Column(name="capture_manual", nullable=false) public boolean captureManual;
    @Column(name="capture_qr", nullable=false) public boolean captureQr;
    @Column(name="capture_barcode", nullable=false) public boolean captureBarcode;
    @Column(name="capture_nfc", nullable=false) public boolean captureNfc;
    @Column(name="capture_automatic", nullable=false) public boolean captureAutomatic;

    @Column(name="source_model_type", nullable=false, length=32) public String sourceModelType;
    @Column(name="source_model_name", length=180) public String sourceModelName;
    @Column(name="last_published_at") public Instant lastPublishedAt;
    @Column(name="updated_by_username", nullable=false, length=80) public String updatedByUsername;
}
