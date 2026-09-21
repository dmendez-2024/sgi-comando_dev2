package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="patrol_protocol_post_scope")
public class PatrolProtocolPostScope extends BaseEntity {
    @Column(name="protocol_id",nullable=false) public UUID protocolId;
    @Column(name="post_id",nullable=false) public UUID postId;
}
