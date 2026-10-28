package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.consignments.Consignment;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.storage.Digests;
import com.cajamarca.sgi.comando.storage.ImageSniffer;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.cajamarca.sgi.comando.territory.MonitorShiftScope;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Único punto de contacto de OBSINT con el resto de SGI: Comando (tenant, usuario, acceso al Punto, MinIO, validación de imágenes).
 * El módulo no usa esas clases en ningún otro lugar; si Comando las cambia, solo se ajusta este archivo.
 */
@ApplicationScoped
public class ObsintPlatform {
    /** Zona horaria de los horarios de las consignas (la misma que usa Comando para códigos y fechas operativas). */
    public static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    /** Rol de IDENT para los cargos de monitoreo; da acceso solo a la consola. */
    public static final String MONITOR_ROLE = MonitorShiftScope.MONITOR_ROLE;

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject StorageService storage;
    @Inject SecurityIdentity identity;
    @Inject EntityManager em;
    @Inject MonitorShiftScope monitorShift;

    public UUID instanceCountryId() { return tenant.instanceCountryId(); }
    public String username() { return scope.username(); }

    /** El Punto existe en el tenant y el usuario puede gestionar su Compañía (misma regla que Consignas). */
    public void requirePoint(UUID pointId) {
        if (pointId == null) throw new BadRequestException("pointId obligatorio");
        PointEntity point = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
        if (point == null) throw new NotFoundException("Punto no encontrado");
        scope.requireCompanyForCompanyRoles(point.companyId);
    }

    /** Consigna de Consignas marcada con el "puesto" OBSINT, tal como está vigente hoy en el Punto. */
    public record LinkedConsignment(UUID pointId, String code, String title, String instruction, boolean temporary, Instant validFrom, Instant validUntil) {
        /** Dentro de su vigencia (las permanentes siempre). */
        public boolean validAt(Instant now) {
            return !temporary || (validFrom == null || !now.isBefore(validFrom)) && (validUntil == null || now.isBefore(validUntil));
        }
    }

    /**
     * Por Punto y por código, las consignas marcadas OBSINT del protocolo de Consignas ACTIVO (estado VIGENTE).
     * Es lo que se puede vincular en la configuración OBSINT y lo que la consola acepta.
     */
    public Map<UUID, Map<String, LinkedConsignment>> obsintConsignments(Collection<UUID> pointIds) {
        Map<UUID, Map<String, LinkedConsignment>> out = new HashMap<>();
        if (pointIds.isEmpty()) return out;
        List<Consignment> rows = Consignment.list(
            "instanceCountryId=?1 and pointId in ?2 and obsint=true and status='VIGENTE' and protocolId in "
                + "(select p.id from ConsignmentProtocol p where p.instanceCountryId=?1 and p.status='ACTIVO') order by code",
            tenant.instanceCountryId(), pointIds);
        for (Consignment c : rows)
            out.computeIfAbsent(c.pointId, k -> new LinkedHashMap<>()).put(c.code, new LinkedConsignment(c.pointId, c.code, c.title, c.instruction,
                "TEMPORARY".equals(c.validityType), c.validityFrom, c.validityUntil));
        return out;
    }

    public Map<String, LinkedConsignment> obsintConsignments(UUID pointId) {
        return obsintConsignments(List.of(pointId)).getOrDefault(pointId, Map.of());
    }

    /**
     * De esos Puntos, los que el usuario ve en la consola: los de las Compañías de su alcance (misma regla que requirePoint)
     * y, si es MONITOR, los de las Compañías de los puestos de Monitoreo donde tiene turno en curso.
     */
    public Set<UUID> visiblePoints(Collection<UUID> pointIds) {
        if (pointIds.isEmpty()) return Set.of();
        Set<UUID> companies = scope.allowedCompanyIdsForCompanyRoles();
        return PointEntity.<PointEntity>list("instanceCountryId=?1 and id in ?2", tenant.instanceCountryId(), pointIds).stream()
            .filter(p -> p.companyId != null && companies.contains(p.companyId)).map(p -> p.id).collect(Collectors.toSet());
    }

    /** Compañías del turno de esa persona (ver MonitorShiftScope); lo usa la prueba del alcance del Monitor. */
    Set<UUID> monitorCompanies(UUID tenantId, Long personaId, Instant now) { return monitorShift.companies(tenantId, personaId, now); }

    /** image/jpeg, image/png o image/webp según los bytes; null si no es una imagen aceptada. */
    public String imageContentType(byte[] bytes) { return ImageSniffer.detect(bytes); }
    public String imageExtension(String contentType) { return ImageSniffer.extension(contentType); }
    public String sha256(byte[] bytes) { return Digests.sha256Hex(bytes); }

    public String evidenceBucket() { return storage.evidenceBucket(); }
    public void put(String bucket, String key, byte[] bytes, String contentType) { storage.put(bucket, key, bytes, contentType); }
    public byte[] read(String bucket, String key) { return storage.read(bucket, key); }
    public void delete(String bucket, String key) { storage.delete(bucket, key); }
}
