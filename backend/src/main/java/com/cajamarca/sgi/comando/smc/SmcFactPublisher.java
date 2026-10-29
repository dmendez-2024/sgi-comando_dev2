package com.cajamarca.sgi.comando.smc;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ServiceUnavailableException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class SmcFactPublisher {
    private static final Logger LOG = Logger.getLogger(SmcFactPublisher.class);
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject TenantContext tenant;
    @Inject SmcFactSources sources;
    @Inject SmcAdapter adapter;
    @ConfigProperty(name="sgi.smc.enabled", defaultValue="false") boolean enabled;
    @ConfigProperty(name="sgi.smc.facts-since") Optional<String> factsSince;
    @ConfigProperty(name="sgi.smc.batch-size", defaultValue="100") int batchSize;
    private final AtomicBoolean running = new AtomicBoolean();

    public record Publication(boolean enabled, boolean busy, int enqueued, int attempted, int requeued) {}

    @Scheduled(every="${sgi.smc.publish-every-seconds:30}s", delayed="30s",
        concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) return;
        try { publish(false); }
        catch (ServiceUnavailableException e) { LOG.warn("Configuración SMC inválida: SGI_SMC_FACTS_SINCE debe omitirse o contener fecha ISO-8601 con zona."); }
        catch (RuntimeException e) { LOG.warn("Publicación SMC pendiente: " + e.getClass().getSimpleName()); }
    }

    public Publication publish(boolean retryRejected) {
        if (!enabled) return new Publication(false, false, 0, 0, 0);
        Optional<Instant> since = configuredSince();
        if (!running.compareAndSet(false, true)) return new Publication(true, true, 0, 0, 0);
        try {
            UUID pe = tenant.instanceCountryId();
            int limit = Math.max(1, Math.min(batchSize, 500));
            int requeued = retryRejected ? QuarkusTransaction.requiringNew().call(() -> requeue(pe, limit)) : 0;
            int added = QuarkusTransaction.requiringNew().call(() -> enqueue(pe, since, limit));
            int attempted = 0;
            while (attempted < limit && QuarkusTransaction.requiringNew().call(() -> deliverOne(pe))) attempted++;
            return new Publication(true, false, added, attempted, requeued);
        } finally { running.set(false); }
    }

    Optional<Instant> configuredSince() {
        Optional<String> value = factsSince.filter(s -> !s.isBlank());
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Instant.parse(value.get().trim()));
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException(jakarta.ws.rs.core.Response.status(503)
                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .entity(Map.of("code","SMC_INVALID_FACTS_SINCE","message",
                    "SGI_SMC_FACTS_SINCE es opcional; omítala o use una fecha ISO-8601 con zona para la carga inicial."))
                .build());
        }
    }

    int enqueue(UUID pe, Optional<Instant> initialSince, int limit) {
        // Solo avanzar sobre rangos cerrados: una transacción de la misma cuenta que
        // aún escribe puede confirmar después con updated_at anterior a este ciclo.
        Instant until = SmcFactSources.instant(em.createNativeQuery("""
            select least(transaction_timestamp(),coalesce(min(xact_start),transaction_timestamp()))
            from pg_stat_activity where datname=current_database() and usename=current_user
                and xact_start is not null
            """).getSingleResult());
        int inserted = 0;
        for (String type : SmcFactSources.TYPES) {
            int initialized = em.createNativeQuery("""
                insert into smc_source_cursor(instance_country_id,source_type,changed_at)
                values(:tenant,:type,:start) on conflict do nothing
                """).setParameter("tenant",pe).setParameter("type",type)
                .setParameter("start",initialSince.orElse(until)).executeUpdate();
            if (initialized > 0) LOG.infof("smc_cursor_initialized source=%s since=%s mode=%s",
                type,initialSince.orElse(until),initialSince.isPresent()?"EXPLICIT_INITIAL_HISTORY":"AUTOMATIC");
            Object[] state = (Object[]) em.createNativeQuery("""
                select changed_at,last_source_id from smc_source_cursor
                where instance_country_id=:tenant and source_type=:type for update
                """).setParameter("tenant",pe).setParameter("type",type).getSingleResult();
            Instant since = SmcFactSources.instant(state[0]);
            UUID after = SmcFactSources.uuid(state[1]);
            if (!since.isBefore(until)) continue;
            List<SmcFactSources.Candidate> page = sources.candidates(pe,type,since,after,until,limit);
            for (SmcFactSources.Candidate candidate : page) {
            SmcFact fact = candidate.fact();
            String payload;
            try { payload = mapper.writeValueAsString(fact.payload()); }
            catch (Exception e) { throw new IllegalStateException("No se pudo serializar el hecho SMC", e); }
            inserted += em.createNativeQuery("""
                insert into smc_fact_delivery(id,instance_country_id,source_type,source_id,event_id,fact_type,payload_json)
                values(:id,:tenant,:sourceType,:sourceId,:event,:factType,:payload)
                on conflict do nothing
                """).setParameter("id", UUID.randomUUID()).setParameter("tenant", pe)
                .setParameter("sourceType", fact.sourceType()).setParameter("sourceId", fact.sourceId())
                .setParameter("event", fact.eventId()).setParameter("factType", fact.factType())
                .setParameter("payload", payload).executeUpdate();
            }
            // Un lote lleno puede tener más filas en la misma fecha: desempate por UUID.
            // Un lote incompleto cierra el rango completo hasta el horizonte seguro.
            Instant next = until;
            UUID lastId = null;
            if (page.size() == limit) {
                SmcFactSources.Candidate last = page.getLast();
                next = last.changedAt(); lastId = last.fact().sourceId();
            }
            em.createNativeQuery("""
                update smc_source_cursor set changed_at=:next,last_source_id=:id,updated_at=current_timestamp
                where instance_country_id=:tenant and source_type=:type
                """).setParameter("next",next).setParameter("id",lastId)
                .setParameter("tenant",pe).setParameter("type",type).executeUpdate();
        }
        return inserted;
    }

    int requeue(UUID pe, int limit) {
        // Acción explícita de administrador: conserva exactamente event_id y payload_json.
        return em.createNativeQuery("""
            update smc_fact_delivery set status='PENDING',next_attempt_at=current_timestamp,updated_at=current_timestamp
            where id in (select id from smc_fact_delivery where instance_country_id=:tenant and status='REJECTED'
                         order by updated_at,id limit :batch for update skip locked)
            """).setParameter("tenant", pe).setParameter("batch", limit).executeUpdate();
    }

    boolean deliverOne(UUID pe) {
        // Lock solo sobre la bandeja. Otra réplica no entrega la misma fila simultáneamente.
        // Si cae después de la recepción remota, el rollback permite reintentar con la misma clave.
        List<?> rows = em.createNativeQuery("""
            select id,payload_json,attempts from smc_fact_delivery
            where instance_country_id=:tenant and status='PENDING' and next_attempt_at<=current_timestamp
            order by next_attempt_at,created_at,id limit 1 for update skip locked
            """).setParameter("tenant", pe).getResultList();
        if (rows.isEmpty()) return false;
        Object[] row = (Object[]) rows.get(0);
        UUID id = SmcFactSources.uuid(row[0]);
        int attempts = ((Number) row[2]).intValue() + 1;
        SmcDeliveryOutcome outcome;
        Integer http = null;
        Map<String,Object> body;
        try { body = mapper.readValue(row[1].toString(), new TypeReference<Map<String,Object>>() {}); }
        catch (Exception e) {
            finish(id, pe, attempts, null, new SmcDeliveryOutcome("REJECTED", null, "INVALID_LOCAL_PAYLOAD"));
            return true;
        }
        try {
            IntegrationResult response = adapter.send(pe, body);
            http = response.statusCode;
            outcome = SmcDeliveryOutcome.from(response);
        } catch (RuntimeException e) {
            String code = e instanceof InterconnectionException failure ? failure.code() : "DELIVERY_FAILURE";
            // No guardar tokens, URLs ni cuerpos de excepción del transporte.
            outcome = new SmcDeliveryOutcome("PENDING", null, code);
        }
        finish(id, pe, attempts, http, outcome);
        return true;
    }

    private void finish(UUID id, UUID pe, int attempts, Integer http, SmcDeliveryOutcome outcome) {
        em.createNativeQuery("""
            update smc_fact_delivery set status=:status,attempts=:attempts,last_status_code=:http,
                last_error=:error,smc_fact_id=:receipt,updated_at=current_timestamp,
                sent_at=case when :status='SENT' then current_timestamp else null end,
                next_attempt_at=:next
            where id=:id and instance_country_id=:tenant
            """).setParameter("status", outcome.status()).setParameter("attempts", attempts)
            .setParameter("http", http).setParameter("error", outcome.error()).setParameter("receipt", outcome.smcFactId())
            .setParameter("next", Instant.now().plusSeconds(SmcDeliveryOutcome.backoffSeconds(attempts)))
            .setParameter("id", id).setParameter("tenant", pe).executeUpdate();
        LOG.infof("smc_delivery id=%s status=%s http=%s attempts=%d", id, outcome.status(), http, attempts);
    }
}
