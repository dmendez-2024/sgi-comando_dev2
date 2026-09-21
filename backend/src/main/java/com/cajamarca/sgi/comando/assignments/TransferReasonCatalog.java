package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name="transfer_reason_catalog")
public class TransferReasonCatalog extends BaseEntity {
    @Column(nullable=false, length=40) public String code;
    @Column(nullable=false, length=160) public String label;
    @Column(nullable=false) public boolean active;
    @Column(name="sort_order", nullable=false) public int sortOrder;
}
