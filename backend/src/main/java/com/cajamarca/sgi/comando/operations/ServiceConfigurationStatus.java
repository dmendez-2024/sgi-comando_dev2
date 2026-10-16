package com.cajamarca.sgi.comando.operations;

import java.util.List;
import java.util.UUID;

/**
 * Mantiene el estado de configuración del Servicio como resumen de sus
 * Puestos operativos. Un Servicio está configurado solo si tiene al menos un
 * Puesto activo y todos sus Puestos activos están configurados.
 */
public final class ServiceConfigurationStatus {
    private ServiceConfigurationStatus() {}

    public static void refresh(UUID instanceCountryId, UUID serviceId) {
        ServiceEntity service=ServiceEntity.find("id=?1 and instanceCountryId=?2",serviceId,instanceCountryId).firstResult();
        if(service==null) return;

        List<PointEntity> activePoints=PointEntity.list("instanceCountryId=?1 and serviceId=?2 and upper(status)='ACTIVE'",instanceCountryId,serviceId);
        if(activePoints.isEmpty()) {
            service.configStatus="TO_CONFIGURE";
            return;
        }
        List<UUID> pointIds=activePoints.stream().map(point->point.id).toList();
        long activePosts=PostEntity.count("instanceCountryId=?1 and pointId in ?2 and upper(commercialStatus)='ACTIVE'",instanceCountryId,pointIds);
        long pendingPosts=PostEntity.count("instanceCountryId=?1 and pointId in ?2 and upper(commercialStatus)='ACTIVE' and (configStatus is null or upper(configStatus)<>'CONFIGURED')",instanceCountryId,pointIds);
        service.configStatus=activePosts>0&&pendingPosts==0?"CONFIGURED":"TO_CONFIGURE";
    }
}
