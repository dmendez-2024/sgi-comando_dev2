package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.outbox.OutboxEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Entrega las sanciones pendientes a DHO y conserva el mismo evento en cada reintento. */
@ApplicationScoped
public class IncidentSanctionWorker {
    private static final Logger LOG = Logger.getLogger(IncidentSanctionWorker.class);

    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject TenantContext tenant;
    @Inject DhoSanctionAdapter dho;
    @ConfigProperty(name = "sgi.dho.sanction.worker-enabled", defaultValue = "true") boolean enabled;
    @ConfigProperty(name = "sgi.dho.sanction.max-attempts", defaultValue = "10") int maxAttempts;
    @ConfigProperty(name = "sgi.dho.sanction.batch-size", defaultValue = "20") int batchSize;

    @Scheduled(every = "${sgi.dho.sanction.worker-every:5s}", delayed = "10s",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) return;
        try { processDue(); }
        catch (RuntimeException error) {
            LOG.warnf("incident_sanction_worker_pending code=%s", safeErrorCode(error));
        }
    }

    public int processDue() {
        if (!enabled) return 0;
        int processed = 0;
        int limit = Math.max(1, Math.min(batchSize, 100));
        while (processed < limit && QuarkusTransaction.requiringNew().call(this::deliverOne)) processed++;
        return processed;
    }

    @SuppressWarnings("unchecked")
    boolean deliverOne() {
        List<UUID> due = em.createNativeQuery("""
            select o.id
            from outbox_event o
            join incident_notification i on i.id=o.aggregate_id and i.instance_country_id=o.instance_country_id
            where o.instance_country_id=:tenant
              and o.aggregate_type='INCIDENT_NOTIFICATION'
              and o.event_type='INCIDENT_SANCTION_REQUESTED'
              and o.published_at is null
              and i.sanction_delivery_status='PENDING'
              and i.sanction_next_attempt_at<=current_timestamp
            order by i.sanction_next_attempt_at,o.created_at,o.id
            limit 1 for update of o skip locked
            """).setParameter("tenant", tenant.instanceCountryId()).getResultList();
        if (due.isEmpty()) return false;

        OutboxEvent event = OutboxEvent.findById(due.getFirst());
        IncidentNotificationEntity incident = IncidentNotificationEntity.find(
            "id=?1 and instanceCountryId=?2", event.aggregateId, event.instanceCountryId).firstResult();
        if (incident == null) {
            event.publishedAt = Instant.now();
            LOG.warnf("incident_sanction_orphan event=%s", event.id);
            return true;
        }

        int attempt = incident.sanctionDeliveryAttempts + 1;
        IntegrationResult result = null;
        DeliveryOutcome outcome;
        try {
            JsonNode payload = mapper.readTree(event.payloadJson);
            result = dho.send(payload);
            outcome = DeliveryOutcome.from(result, mapper);
        } catch (RuntimeException error) {
            outcome = new DeliveryOutcome("PENDING", safeErrorCode(error));
        } catch (Exception error) {
            outcome = new DeliveryOutcome("FAILED", "INVALID_LOCAL_PAYLOAD");
        }

        incident.sanctionDeliveryAttempts = attempt;
        incident.sanctionLastHttpStatus = result == null ? null : result.statusCode;
        incident.sanctionLastError = outcome.errorCode();
        if ("SENT".equals(outcome.status())) {
            Instant now = Instant.now();
            event.publishedAt = now;
            incident.sanctionDeliveryStatus = "SENT";
            incident.sanctionSentAt = now;
            incident.sanctionNextAttemptAt = null;
            incident.sanctionLastError = null;
        } else if ("FAILED".equals(outcome.status()) || attempt >= Math.max(1, maxAttempts)) {
            incident.sanctionDeliveryStatus = "FAILED";
            incident.sanctionNextAttemptAt = null;
            if (incident.sanctionLastError == null) incident.sanctionLastError = "MAX_ATTEMPTS_REACHED";
        } else {
            incident.sanctionDeliveryStatus = "PENDING";
            incident.sanctionNextAttemptAt = Instant.now().plusSeconds(backoffSeconds(attempt));
        }
        LOG.infof("incident_sanction_delivery incident=%s event=%s status=%s http=%s attempt=%d",
            incident.id, event.id, incident.sanctionDeliveryStatus, incident.sanctionLastHttpStatus, attempt);
        return true;
    }

    static long backoffSeconds(int attempt) {
        return Math.min(3600L, 30L << Math.min(Math.max(attempt - 1, 0), 7));
    }

    static String safeErrorCode(Throwable error) {
        return error instanceof InterconnectionException integration ? integration.code() : "DELIVERY_FAILURE";
    }

    record DeliveryOutcome(String status, String errorCode) {
        static DeliveryOutcome from(IntegrationResult result, ObjectMapper mapper) {
            if (result.statusCode >= 200 && result.statusCode < 300) {
                if (result.body != null && !result.body.isBlank()) {
                    try {
                        JsonNode response = mapper.readTree(result.body);
                        if (response.has("success") && !response.path("success").asBoolean())
                            return new DeliveryOutcome("FAILED", "DHO_REJECTED");
                    } catch (Exception ignored) {
                        // Un 2xx es suficiente aunque el cuerpo no sea JSON.
                    }
                }
                return new DeliveryOutcome("SENT", null);
            }
            if (result.statusCode == 408 || result.statusCode == 429 || result.statusCode >= 500)
                return new DeliveryOutcome("PENDING", "DHO_HTTP_" + result.statusCode);
            return new DeliveryOutcome("FAILED", "DHO_HTTP_" + result.statusCode);
        }
    }
}
