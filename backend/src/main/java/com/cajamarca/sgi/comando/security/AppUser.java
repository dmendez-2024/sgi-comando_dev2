package com.cajamarca.sgi.comando.security;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.*;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="app_user")
@UserDefinition
public class AppUser extends PanacheEntityBase {
    @Id public UUID id;
    @Username @Column(nullable=false, unique=true) public String username;
    @Password(PasswordType.MCF) @Column(name="password_hash", nullable=false) public String passwordHash;
    @Roles @Column(nullable=false) public String roles;
    @Column(name="display_name", nullable=false) public String displayName;
    @Column(name="instance_country_id", nullable=false) public UUID instanceCountryId;
    @Column(nullable=false) public boolean active;
}
