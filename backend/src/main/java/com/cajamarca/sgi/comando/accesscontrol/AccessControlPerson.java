package com.cajamarca.sgi.comando.accesscontrol;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;

/** Identidad única reutilizada por las autorizaciones de distintos Clientes y Puntos. */
@Entity
@Table(name="access_control_person")
public class AccessControlPerson extends BaseEntity {
    @Column(name="object_type",nullable=false,length=16) public String objectType;
    @Column(nullable=false,length=40) public String identification;
    @Column(name="identification_normalized",nullable=false,length=40) public String identificationNormalized;
    @Column(name="last_names",nullable=false,length=180) public String lastNames;
    @Column(name="first_names",nullable=false,length=180) public String firstNames;
    @Column(name="photo_data") public byte[] photoData;
    @Column(name="photo_content_type",length=80) public String photoContentType;
    @Column(name="photo_filename",length=255) public String photoFilename;
    @Column(name="qr_token_hash",length=64) public String qrTokenHash;
    @Column(name="qr_png") public byte[] qrPng;
    @Column(name="qr_generated_at") public java.time.Instant qrGeneratedAt;
    @Column(name="qr_revoked_at") public java.time.Instant qrRevokedAt;
    @Column(name="created_by_username",nullable=false,length=120) public String createdByUsername;
    @Column(name="updated_by_username",nullable=false,length=120) public String updatedByUsername;
}
