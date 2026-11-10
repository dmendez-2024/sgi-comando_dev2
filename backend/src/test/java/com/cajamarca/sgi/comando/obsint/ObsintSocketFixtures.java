package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.socket.SocketConfig;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;

/** Datos propios de estas pruebas; no dependen de usuarios o catálogos UAT. */
record ObsintSocketFixtures(UUID tenant,UUID employee,UUID task,UUID point) {
    /** Cargo del monitorista que envía en estas pruebas; su usuario es "socket-test". */
    static final String SENDER_ROLE="Agente de Consola de Monitoreo Senior";
    static ObsintSocketFixtures create(EntityManager em) {
        UUID tenant=UUID.randomUUID(),point=UUID.randomUUID();
        ObsintProtocol p=new ObsintProtocol(); p.instanceCountryId=tenant; p.seriesId=UUID.randomUUID();
        p.pointId=point; p.code="SOCKET-TEST"; p.versionNo=1; p.status="ACTIVO"; p.updatedBy="socket-test"; p.persist();
        ObsintConsigna c=new ObsintConsigna(); c.instanceCountryId=tenant; c.protocolId=p.id; c.pointId=point;
        c.code="SOCKET-TEST"; c.title="Prueba"; c.instruction="Verificar"; c.category="MOBILITY"; c.timeLimitSeconds=60; c.position=1; c.persist();
        var caso=ObsintCase.open(tenant,UUID.randomUUID().toString(),"socket-test",List.of(c),Instant.now());
        caso.persist(); caso.startTask(caso.tasks.getFirst(),Instant.now()); em.flush();
        return new ObsintSocketFixtures(tenant,UUID.randomUUID(),caso.tasks.getFirst().id,point);
    }
    ObsintService service(EntityManager em,SocketConfig config,Event<ObsintMessageQueued> queued,List<ObsintAgentRecipients.Recipient> agents) {
        ObsintService service=new ObsintService();service.em=em;service.socket=config;service.queued=queued;
        service.platform=new ObsintPlatform() {
            public UUID instanceCountryId() { return ObsintSocketFixtures.this.tenant(); }
            public String username() { return "socket-test"; }
        };
        service.monitorScope=new com.cajamarca.sgi.comando.territory.MonitorShiftScope() { public String cargoNow() { return SENDER_ROLE; } };
        service.recipients=new ObsintAgentRecipients() { public List<Recipient> onShift(UUID tenant,UUID point,Instant now) { return agents; } };
        return service;
    }
    List<ObsintAgentRecipients.Recipient> agents() {
        return List.of(new ObsintAgentRecipients.Recipient(employee,UUID.randomUUID(),UUID.randomUUID(),"Puesto uno",9428L,"Agente uno"),
            new ObsintAgentRecipients.Recipient(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Puesto dos",9429L,"Agente dos"));
    }
    ObsintAgentMessage send(EntityManager em,SocketConfig config,Event<ObsintMessageQueued> queued,boolean agent) {
        return service(em,config,queued,agent?agents().subList(0,1):List.of()).send(task,"Revisar portón");
    }
}
