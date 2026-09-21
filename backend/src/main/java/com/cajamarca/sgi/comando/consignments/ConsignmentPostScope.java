package com.cajamarca.sgi.comando.consignments;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="consignment_post_scope")
public class ConsignmentPostScope extends BaseEntity {
 @Column(name="consignment_id",nullable=false) public UUID consignmentId;
 @Column(name="post_id",nullable=false) public UUID postId;
}
