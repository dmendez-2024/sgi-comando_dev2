package com.cajamarca.sgi.comando.bitacora;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="logbook_protocol_post_scope")
public class LogbookProtocolPostScope extends BaseEntity {
 @Column(name="protocol_id",nullable=false) public UUID protocolId;
 @Column(name="post_id",nullable=false) public UUID postId;
}
