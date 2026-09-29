package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.patrols.*;
import com.cajamarca.sgi.comando.storage.StandardImageStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;

/** Patrullas que el agente puede ejecutar: solo protocolos publicados y activos en su Puesto. */
@ApplicationScoped
public class OperatorPatrols {
    public record Applicable(PatrolProtocol protocol, PatrolDefinition patrol, PatrolCheckpoint checkpoint) {}

    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="sgi.evidence.default-radius-m") int defaultRadius;

    /** Radio (m) para Hitos sin radio propio. Se expone por método: los campos de un bean CDI no se leen a través de su proxy. */
    public int defaultRadius() { return defaultRadius; }

    @SuppressWarnings("unchecked")
    List<PatrolProtocol> activeFor(UUID postId) {
        List<UUID> ids = em.createNativeQuery("""
            select p.id from patrol_protocol p where p.instance_country_id=:t and p.status='ACTIVO'
            and exists (select 1 from patrol_protocol_post_scope s where s.protocol_id=p.id and s.post_id=:post and s.instance_country_id=p.instance_country_id)""")
            .setParameter("t", tenant.instanceCountryId()).setParameter("post", postId).getResultList();
        return ids.isEmpty() ? List.of() : PatrolProtocol.list("id in ?1 order by code", ids);
    }

    public ArrayNode runtime(UUID postId) {
        ArrayNode out = mapper.createArrayNode();
        for (PatrolProtocol p : activeFor(postId)) {
            for (PatrolDefinition d : PatrolDefinition.<PatrolDefinition>list("protocolId=?1 and instanceCountryId=?2 order by code", p.id, tenant.instanceCountryId())) {
                ObjectNode n = out.addObject().put("protocolId", p.id.toString()).put("protocolCode", p.code).put("protocolVersion", p.versionNo)
                    .put("patrolId", d.id.toString()).put("code", d.code).put("name", d.name).put("structureType", d.structureType)
                    .put("scheduleType", d.scheduleType).put("sequenceType", d.sequenceType)
                    .put("windowStart", d.windowStart == null ? null : d.windowStart.toString())
                    .put("windowEnd", d.windowEnd == null ? null : d.windowEnd.toString());
                ArrayNode cps = n.putArray("checkpoints");
                for (PatrolCheckpoint c : PatrolCheckpoint.<PatrolCheckpoint>list("patrolId=?1 and instanceCountryId=?2 order by sortOrder", d.id, tenant.instanceCountryId())) {
                    cps.addObject().put("checkpointId", c.id.toString()).put("code", c.code).put("name", c.name).put("description", c.description)
                        .put("sortOrder", c.sortOrder).put("requiresEvidence", c.requiresEvidence)
                        .put("evidenceMinCount", c.evidenceMinCount).put("evidenceMaxCount", c.evidenceMaxCount)
                        .put("hasStandardImage", StandardImageStore.has(c.standardImageObjectKey, c.standardImageData))
                        .put("standardImageVersion", c.standardImageVersion).put("standardImageNotes", c.standardImageNotes)
                        .put("latitude", c.latitude).put("longitude", c.longitude).put("radiusM", c.radiusM == null ? defaultRadius : c.radiusM);
                }
            }
        }
        return out;
    }

    public Applicable requireCheckpoint(UUID checkpointId, UUID postId) {
        PatrolCheckpoint c = PatrolCheckpoint.find("id=?1 and instanceCountryId=?2", checkpointId, tenant.instanceCountryId()).firstResult();
        PatrolDefinition d = c == null ? null : PatrolDefinition.find("id=?1 and instanceCountryId=?2", c.patrolId, tenant.instanceCountryId()).firstResult();
        PatrolProtocol p = d == null ? null : activeFor(postId).stream().filter(x -> x.id.equals(d.protocolId)).findFirst().orElse(null);
        if (p == null) throw new BadRequestException("El Hito no pertenece a una patrulla activa de este puesto");
        return new Applicable(p, d, c);
    }
}
