package com.cajamarca.sgi.comando.impulses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Saldo de Impulsos de un Operador, calculado solo con las evaluaciones guardadas por el motor. La app lo muestra sin recalcular. */
@ApplicationScoped
public class ImpulseLedger {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @SuppressWarnings("unchecked")
    public ObjectNode summary(UUID tenant, UUID employee) {
        Map<String, Object[]> totals = new HashMap<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
              select skill_code, coalesce(sum(amount),0), count(*) filter (where decision='AWARDED'), max(evaluated_at) filter (where decision='AWARDED')
              from impulse_evaluation where instance_country_id=:t and employee_id=:e group by skill_code""")
            .setParameter("t", tenant).setParameter("e", employee).getResultList()) totals.put(r[0].toString(), r);
        Set<String> active = new HashSet<>((List<String>) em.createNativeQuery("""
              select skill_code from (select r.*, row_number() over (partition by code order by version_no desc) n
                                      from impulse_rule r where instance_country_id=:t and effective_from<=now()) v
              where n=1 and active""").setParameter("t", tenant).getResultList());

        ObjectNode n = mapper.createObjectNode().put("employeeId", employee.toString())
            .put("impulsesPerTenth", ImpulseSkills.IMPULSES_PER_TENTH).put("maxLevel", ImpulseSkills.MAX_TENTH / 10.0);
        ArrayNode skills = n.putArray("skills");
        BigDecimal total = BigDecimal.ZERO;
        int tenthSum = 0;
        for (ImpulseSkills.Skill s : ImpulseSkills.ALL) {
            Object[] t = totals.get(s.code());
            BigDecimal impulses = t == null ? BigDecimal.ZERO : (BigDecimal) t[1];
            int tenth = Math.min(ImpulseSkills.MAX_TENTH, impulses.divide(BigDecimal.valueOf(ImpulseSkills.IMPULSES_PER_TENTH), 0, RoundingMode.FLOOR).intValue());
            // Impulsos acumulados hacia la siguiente décima, con un decimal (una consigna puede dar 0,2).
            double progress = tenth >= ImpulseSkills.MAX_TENTH ? ImpulseSkills.IMPULSES_PER_TENTH
                : impulses.subtract(BigDecimal.valueOf((long) tenth * ImpulseSkills.IMPULSES_PER_TENTH)).setScale(1, RoundingMode.FLOOR).doubleValue();
            total = total.add(impulses);
            tenthSum += tenth;
            ObjectNode item = skills.addObject().put("code", s.code()).put("name", s.name())
                .put("impulses", impulses.setScale(1, RoundingMode.HALF_UP).doubleValue()).put("levelTenth", tenth).put("level", tenth / 10.0)
                .put("progressToNext", progress).put("awards", t == null ? 0 : ((Number) t[2]).intValue()).put("ruleActive", active.contains(s.code()));
            if (t != null && t[3] != null) item.put("lastAwardAt", t[3].toString());
        }
        n.put("totalImpulses", total.setScale(1, RoundingMode.HALF_UP).doubleValue());
        // Índice 0–10: promedio del nivel de las ocho habilidades (0–5) llevado a escala de 10.
        n.put("index", BigDecimal.valueOf(tenthSum * 2L).divide(BigDecimal.valueOf(10L * ImpulseSkills.ALL.size()), 1, RoundingMode.HALF_UP).doubleValue());
        ArrayNode recent = n.putArray("recent");
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
              select e.id, e.skill_code, e.rule_code, r.description, e.source_type, e.source_id, e.decision, e.reason, e.amount, e.visint_status, e.occurred_at, e.evaluated_at
              from impulse_evaluation e join impulse_rule r on r.id=e.rule_id
              where e.instance_country_id=:t and e.employee_id=:e order by e.evaluated_at desc limit 20""")
            .setParameter("t", tenant).setParameter("e", employee).getResultList()) {
            recent.addObject().put("evaluationId", r[0].toString()).put("skillCode", r[1].toString()).put("skillName", ImpulseSkills.name(r[1].toString()))
                .put("ruleCode", r[2].toString()).put("description", r[3].toString()).put("sourceType", r[4].toString()).put("sourceId", r[5].toString())
                .put("decision", r[6].toString()).put("reason", r[7] == null ? "" : r[7].toString())
                .put("amount", ((BigDecimal) r[8]).doubleValue()).put("visintStatus", r[9] == null ? "" : r[9].toString())
                .put("occurredAt", r[10].toString()).put("evaluatedAt", r[11].toString());
        }
        return n;
    }
}
