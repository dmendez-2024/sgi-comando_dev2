package com.cajamarca.sgi.comando.operations;
import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="post")
public class PostEntity extends BaseEntity {
    @Column(name="point_id",nullable=false) public UUID pointId;
    @Column(nullable=false) public String code;
    @Column(nullable=false) public String name;
    @Column(nullable=false) public String format;
    @Column(precision=8,scale=2,nullable=false) public BigDecimal fhe;
    @Column(nullable=false) public String tier;
    @Column(name="config_status",nullable=false) public String configStatus;
}
