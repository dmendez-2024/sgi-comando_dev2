package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="user_operational_scope")
public class UserOperationalScope extends BaseEntity {
    @Column(nullable=false) public String username;
    @Column(name="scope_type", nullable=false) public String scopeType;
    @Column(name="scope_id") public UUID scopeId;
}
