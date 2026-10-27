package com.cajamarca.sgi.comando.smc;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.security.IdentIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

/** Administración nacional de la integración, no disponible para tokens de SGI Operador. */
@Path("/api/v1/integrations/smc")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL"})
@ApplicationScoped
public class SmcPublishResource {
    @Inject SmcFactPublisher publisher;
    @Inject EntityManager em;
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;

    @POST @Path("/publish")
    public SmcFactPublisher.Publication publish(@QueryParam("retryRejected") @DefaultValue("false") boolean retryRejected) {
        activeTenant();
        return publisher.publish(retryRejected);
    }

    @GET @Path("/deliveries")
    public List<Map<String,Object>> deliveries(@QueryParam("status") String status) {
        if (status != null && !Set.of("PENDING","SENT","REJECTED").contains(status))
            throw new BadRequestException("status debe ser PENDING, SENT o REJECTED");
        UUID pe = activeTenant();
        return QuarkusTransaction.requiringNew().call(() -> {
            var query = em.createNativeQuery("""
                select event_id,source_type,source_id,fact_type,status,attempts,last_status_code,
                       last_error,smc_fact_id,created_at,sent_at,next_attempt_at
                from smc_fact_delivery where instance_country_id=:tenant
                """ + (status == null ? "" : " and status=:status") + " order by created_at desc,id limit 100")
                .setParameter("tenant", pe);
            if (status != null) query.setParameter("status", status);
            List<Map<String,Object>> result = new ArrayList<>();
            String[] keys = {"eventId","sourceType","sourceId","factType","status","attempts","lastStatusCode",
                "lastError","smcFactId","createdAt","sentAt","nextAttemptAt"};
            for (Object raw : query.getResultList()) {
                Object[] row = (Object[]) raw;
                Map<String,Object> item = new LinkedHashMap<>();
                for (int i=0; i<keys.length; i++) item.put(keys[i], row[i] == null ? null :
                    row[i] instanceof Number ? row[i] : row[i].toString());
                result.add(item);
            }
            return result;
        });
    }

    private UUID activeTenant() {
        UUID pe = tenant.instanceCountryId();
        UUID tokenPe = IdentIdentity.instanceCountryId(identity);
        if (tokenPe != null && !pe.equals(tokenPe)) throw new ForbiddenException("Instancia fuera del alcance del token");
        return pe;
    }
}
