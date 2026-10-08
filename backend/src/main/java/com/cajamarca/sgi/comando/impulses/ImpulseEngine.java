package com.cajamarca.sgi.comando.impulses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

/**
 * Motor de Impulsos: recorre los hechos operativos ya guardados (relevos, bitácoras, patrullas, consignas), espera el resultado de
 * VISINT cuando la tarea lo tiene y aplica la versión vigente de cada regla de impulse_rule. Cada (regla, hecho) se evalúa una sola
 * vez: el sorteo queda guardado en impulse_evaluation, que es también el ledger del Operador.
 */
@ApplicationScoped
public class ImpulseEngine {
    private static final Logger LOG = Logger.getLogger(ImpulseEngine.class);
    private static final int BATCH = 100;

    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="sgi.impulses.engine-enabled", defaultValue="true") boolean enabled;
    /** Espera antes de evaluar un hecho, para que lleguen sus fotos (se suben aparte del registro). */
    @ConfigProperty(name="sgi.impulses.settle-seconds", defaultValue="60") long settleSeconds;
    /** Tiempo máximo esperando una revisión VISINT en error; después el hecho se cierra sin Impulsos. */
    @ConfigProperty(name="sgi.impulses.visint-wait-hours", defaultValue="24") long visintWaitHours;
    private final SecureRandom random = new SecureRandom();

    record Rule(UUID id, UUID tenant, String code, int version, String skill, String source, boolean requiresVisint,
                double probability, BigDecimal min, BigDecimal max, JsonNode params) {}
    /** Hecho operativo candidato: eligible=false lleva el motivo; visintSql (con visintParams) devuelve (target_id, capture_no, estado VISINT, received_at). */
    record Fact(UUID sourceId, UUID employee, UUID assignment, Instant occurredAt, boolean eligible, String reason, String visintSql, Map<String,Object> visintParams) {
        Fact(UUID sourceId, UUID employee, UUID assignment, Instant occurredAt, boolean eligible, String reason) { this(sourceId, employee, assignment, occurredAt, eligible, reason, null, Map.of()); }
    }
    enum Gate { WAIT, NOT_REQUESTED, PASSED, FAILED, NO_RESULT }

    @Scheduled(every="30s", delayed="20s", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) return;
        try { evaluateDue(); } catch (RuntimeException e) { LOG.warn("Evaluación de Impulsos pendiente: " + e.getMessage()); }
    }

    public int evaluateDue() {
        int done = 0;
        for (Rule rule : QuarkusTransaction.requiringNew().call(this::activeRules)) {
            for (Fact fact : QuarkusTransaction.requiringNew().call(() -> candidates(rule)))
                if (QuarkusTransaction.requiringNew().call(() -> evaluate(rule, fact))) done++;
        }
        return done;
    }

    /** Versión vigente de cada regla por Instancia PE; si la última versión está inactiva, la regla no se aplica. */
    @SuppressWarnings("unchecked")
    List<Rule> activeRules() {
        List<Object[]> rows = em.createNativeQuery("""
            select id,instance_country_id,code,version_no,skill_code,source_type,requires_visint_pass,probability,amount_min,amount_max,params_json,active
            from (select r.*, row_number() over (partition by instance_country_id, code order by version_no desc) n
                  from impulse_rule r where effective_from<=now()) v
            where n=1 and active order by instance_country_id, code""").getResultList();
        List<Rule> rules = new ArrayList<>();
        for (Object[] r : rows) {
            JsonNode params;
            try { params = mapper.readTree(r[10].toString()); } catch (Exception e) { params = mapper.createObjectNode(); }
            rules.add(new Rule((UUID) r[0], (UUID) r[1], r[2].toString(), ((Number) r[3]).intValue(), r[4].toString(), r[5].toString(),
                Boolean.TRUE.equals(r[6]), ((Number) r[7]).doubleValue(), (BigDecimal) r[8], (BigDecimal) r[9], params));
        }
        return rules;
    }

    private static final String NOT_EVALUATED =
        " and not exists (select 1 from impulse_evaluation e where e.instance_country_id=:t and e.rule_code=:code and e.source_type=:source and e.source_id=%s)";

    @SuppressWarnings("unchecked")
    List<Fact> candidates(Rule rule) {
        Instant cutoff = Instant.now().minusSeconds(settleSeconds);
        String sql = switch (rule.source()) {
            case "RELIEF" -> """
                select r.id, r.incoming_employee_id, s.assignment_id, r.executed_at, r.planned_at,
                       exists(select 1 from operator_relief_evidence ev where ev.instance_country_id=r.instance_country_id and ev.event_id=r.id and ev.purpose='entrant_full')
                from relief_event r join operator_relief_submission s on s.id=r.id and s.instance_country_id=r.instance_country_id
                where r.instance_country_id=:t and r.incoming_employee_id is not null and s.received_at<:cutoff""" + NOT_EVALUATED.formatted("r.id");
            case "LOGBOOK" -> """
                select r.id, r.employee_id, r.assignment_id, r.captured_at from operator_logbook_record r
                where r.instance_country_id=:t and r.status='REGISTERED' and r.received_at<:cutoff""" + NOT_EVALUATED.formatted("r.id");
            case "PATROL" -> """
                select pe.id, pe.employee_id, pe.assignment_id, pe.finished_at, pe.result from patrol_execution pe
                where pe.instance_country_id=:t and pe.employee_id is not null and pe.finished_at is not null and pe.finished_at<:cutoff""" + NOT_EVALUATED.formatted("pe.id");
            case "CONSIGNMENT" -> """
                select cc.id, cc.employee_id, cc.assignment_id, cc.confirmed_at, cc.result, cc.consignment_id from consignment_compliance cc
                where cc.instance_country_id=:t and cc.confirmed_at<:cutoff""" + NOT_EVALUATED.formatted("cc.id");
            default -> null; // Fuente sin productor todavía (novedades, km, liderazgo, QR): la regla existe pero no hay hechos que evaluar.
        };
        if (sql == null) return List.of();
        List<Object[]> rows = em.createNativeQuery(sql + " limit " + BATCH).setParameter("t", rule.tenant()).setParameter("cutoff", cutoff)
            .setParameter("code", rule.code()).setParameter("source", rule.source()).getResultList();
        List<Fact> facts = new ArrayList<>();
        for (Object[] r : rows) facts.add(fact(rule, r));
        return facts;
    }

    private Fact fact(Rule rule, Object[] r) {
        UUID id = (UUID) r[0], employee = (UUID) r[1], assignment = (UUID) r[2];
        Instant at = instant(r[3]);
        return switch (rule.source()) {
            case "RELIEF" -> {
                if ("REL_A_TIEMPO".equals(rule.code())) {
                    long tolerance = rule.params().path("toleranceMinutes").asLong(15);
                    boolean onTime = !at.isAfter(instant(r[4]).plus(Duration.ofMinutes(tolerance)));
                    yield new Fact(id, employee, assignment, at, onTime, onTime ? null : "Relevo fuera de la tolerancia de " + tolerance + " min");
                }
                boolean full = Boolean.TRUE.equals(r[5]);
                yield new Fact(id, employee, assignment, at, full, full ? null : "El relevo no tiene foto de cuerpo completo");
            }
            case "LOGBOOK" -> new Fact(id, employee, assignment, at, true, null,
                "select te.target_id, te.capture_no, v.status, te.received_at from task_execution te left join visual_review v on v.task_execution_id=te.id " +
                "where te.instance_country_id=:t and te.target_type='LOGBOOK_FIELD' and te.group_id=:g", Map.<String,Object>of("g", id));
            case "PATROL" -> {
                boolean complete = "COMPLETA".equals(String.valueOf(r[4]));
                yield new Fact(id, employee, assignment, at, complete, complete ? null : "Patrulla incompleta",
                    "select te.target_id, te.capture_no, v.status, te.received_at from task_execution te left join visual_review v on v.task_execution_id=te.id " +
                    "where te.instance_country_id=:t and te.patrol_execution_id=:g", Map.<String,Object>of("g", id));
            }
            case "CONSIGNMENT" -> {
                boolean done = "CUMPLIDA".equals(String.valueOf(r[4]));
                yield new Fact(id, employee, assignment, at, done, done ? null : "Consigna registrada como " + r[4],
                    "select te.target_id, te.capture_no, v.status, te.received_at from task_execution te " +
                    "join consignment_evidence ce on ce.id=te.target_id and ce.instance_country_id=te.instance_country_id " +
                    "left join visual_review v on v.task_execution_id=te.id " +
                    "where te.instance_country_id=:t and te.target_type='CONSIGNMENT_EVIDENCE' and te.group_id=:g and ce.consignment_id=:c", Map.<String,Object>of("g", assignment, "c", r[5]));
            }
            default -> throw new IllegalStateException(rule.source());
        };
    }

    /** Evalúa y guarda una vez; false si el hecho todavía espera a VISINT o ya fue evaluado por otro nodo. */
    boolean evaluate(Rule rule, Fact fact) {
        Gate gate = fact.visintSql() == null ? Gate.NOT_REQUESTED : gate(rule.tenant(), fact.visintSql(), fact.visintParams());
        if (gate == Gate.WAIT) return false;
        String decision, reason = null;
        Double draw = null;
        BigDecimal amount = BigDecimal.ZERO;
        if (!fact.eligible()) { decision = "NOT_ELIGIBLE"; reason = fact.reason(); }
        else if (rule.requiresVisint() && gate == Gate.FAILED) { decision = "NOT_ELIGIBLE"; reason = "VISINT no aprobó la evidencia"; }
        else if (rule.requiresVisint() && gate == Gate.NO_RESULT) { decision = "NOT_ELIGIBLE"; reason = "VISINT no entregó resultado"; }
        else {
            draw = random.nextDouble();
            if (draw >= rule.probability()) { decision = "NO_AWARD"; reason = "El sorteo no otorgó Impulsos"; }
            else {
                BigDecimal span = rule.max().subtract(rule.min());
                amount = rule.min().add(span.multiply(BigDecimal.valueOf(random.nextDouble()))).setScale(1, RoundingMode.HALF_UP);
                if (amount.signum() > 0) decision = "AWARDED";
                else { decision = "NO_AWARD"; reason = "El sorteo dio 0 Impulsos"; amount = BigDecimal.ZERO; }
            }
        }
        int inserted = em.createNativeQuery("""
            insert into impulse_evaluation(id,instance_country_id,rule_id,rule_code,rule_version,skill_code,source_type,source_id,employee_id,assignment_id,
                                           occurred_at,visint_status,decision,reason,draw,amount,evaluated_at)
            values(:id,:t,:rule,:code,:version,:skill,:source,:sourceId,:employee,:assignment,:occurred,:visint,:decision,:reason,:draw,:amount,now())
            on conflict (instance_country_id, rule_code, source_type, source_id) do nothing""")
            .setParameter("id", UUID.randomUUID()).setParameter("t", rule.tenant()).setParameter("rule", rule.id()).setParameter("code", rule.code())
            .setParameter("version", rule.version()).setParameter("skill", rule.skill()).setParameter("source", rule.source()).setParameter("sourceId", fact.sourceId())
            .setParameter("employee", fact.employee()).setParameter("assignment", fact.assignment()).setParameter("occurred", fact.occurredAt())
            .setParameter("visint", gate.name()).setParameter("decision", decision).setParameter("reason", reason)
            .setParameter("draw", draw == null ? null : BigDecimal.valueOf(draw).setScale(5, RoundingMode.HALF_UP)).setParameter("amount", amount)
            .executeUpdate();
        return inserted == 1;
    }

    /** Resultado VISINT de las fotos del hecho: cuenta la última captura de cada destino (un "no cumple" permite repetir la foto). */
    @SuppressWarnings("unchecked")
    Gate gate(UUID tenant, String sql, Map<String,Object> params) {
        var query = em.createNativeQuery(sql).setParameter("t", tenant);
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();
        Map<Object, Object[]> latest = new HashMap<>();
        for (Object[] r : rows) {
            Object[] current = latest.get(r[0]);
            if (current == null || order(r).compareTo(order(current)) > 0) latest.put(r[0], r);
        }
        boolean passed = false, failed = false;
        for (Object[] r : latest.values()) {
            String status = r[2] == null ? null : r[2].toString();
            if (status == null) continue;
            switch (status) {
                case "PASSED" -> passed = true;
                case "FAILED" -> failed = true;
                case "ERROR_FINAL" -> {
                    if (instant(r[3]).isAfter(Instant.now().minus(Duration.ofHours(visintWaitHours)))) return Gate.WAIT;
                    return Gate.NO_RESULT;
                }
                default -> { return Gate.WAIT; }
            }
        }
        return failed ? Gate.FAILED : passed ? Gate.PASSED : Gate.NOT_REQUESTED;
    }

    private static String order(Object[] r) {
        return String.format("%06d|%s", r[1] == null ? 0 : ((Number) r[1]).intValue(), instant(r[3]));
    }

    private static Instant instant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof OffsetDateTime d) return d.toInstant();
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        return Instant.parse(o.toString());
    }
}
