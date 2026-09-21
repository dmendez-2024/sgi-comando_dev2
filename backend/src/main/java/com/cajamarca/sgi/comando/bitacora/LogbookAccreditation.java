package com.cajamarca.sgi.comando.bitacora;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="logbook_accreditation", uniqueConstraints={
    @UniqueConstraint(name="uq_logbook_accreditation_code_protocol", columnNames={"instance_country_id","protocol_id","code"})
})
public class LogbookAccreditation extends BaseEntity {
    @Column(name="protocol_id", nullable=false) public UUID protocolId;
    @Column(name="code", nullable=false, length=32) public String code;
    @Column(name="name", nullable=false, length=180) public String name;
    @Column(name="description", nullable=false, length=1000) public String description;

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
}
