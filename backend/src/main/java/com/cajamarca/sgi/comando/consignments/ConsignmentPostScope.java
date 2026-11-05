package com.cajamarca.sgi.comando.consignments;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="consignment_post_scope")
public class ConsignmentPostScope extends BaseEntity {
 @Column(name="consignment_id",nullable=false) public UUID consignmentId;
 /** Puesto físico; es nulo cuando el alcance corresponde a un dispositivo electrónico. */
 @Column(name="post_id") public UUID postId;
 @Column(name="electronic_device_id") public UUID electronicDeviceId;
}
