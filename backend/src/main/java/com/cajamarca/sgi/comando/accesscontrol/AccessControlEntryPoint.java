package com.cajamarca.sgi.comando.accesscontrol;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="access_control_entry_point")
@IdClass(AccessControlEntryPoint.Key.class)
public class AccessControlEntryPoint extends PanacheEntityBase {
    @Id @Column(name="entry_id") public UUID entryId;
    @Id @Column(name="point_id") public UUID pointId;
    @Column(name="instance_country_id",nullable=false) public UUID instanceCountryId;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    public static class Key implements java.io.Serializable {
        public UUID entryId; public UUID pointId;
        public Key() {}
        public Key(UUID entryId,UUID pointId){this.entryId=entryId;this.pointId=pointId;}
        @Override public boolean equals(Object value){return value instanceof Key other&&java.util.Objects.equals(entryId,other.entryId)&&java.util.Objects.equals(pointId,other.pointId);}
        @Override public int hashCode(){return java.util.Objects.hash(entryId,pointId);}
    }
}
