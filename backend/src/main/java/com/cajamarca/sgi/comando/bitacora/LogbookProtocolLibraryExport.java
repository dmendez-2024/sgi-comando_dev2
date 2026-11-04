package com.cajamarca.sgi.comando.bitacora;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "logbook_protocol_library_export")
public class LogbookProtocolLibraryExport extends BaseEntity {
    @Column(name = "source_protocol_id", nullable = false) public UUID sourceProtocolId;
    @Column(name = "scope", nullable = false, length = 16) public String scope;
    @Column(name = "company_id") public UUID companyId;
    @Column(name = "exported_by_username", nullable = false, length = 80) public String exportedByUsername;
}
