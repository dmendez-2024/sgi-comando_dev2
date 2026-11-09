package com.cajamarca.sgi.comando.obsint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class ObsintAgentRecipients {
    @Inject EntityManager em;
    public record Recipient(UUID employeeId,UUID assignmentId,UUID postId,String postName,Long personaId,String fullName) {}
    public List<Recipient> onShift(UUID tenantId,UUID pointId,Instant now) {
        List<?> rows=em.createNativeQuery("""
            select coalesce(a.actual_employee_id,a.employee_id),a.id,s.post_id,p.name,e.persona_id,e.full_name,
                   count(e.id) over(partition by a.id)
            from operational_assignment a
            join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
            join post p on p.id=s.post_id and p.instance_country_id=s.instance_country_id
            left join employee_operational_snapshot e on e.employee_id=coalesce(a.actual_employee_id,a.employee_id) and e.instance_country_id=a.instance_country_id
            where a.instance_country_id=:tenant and p.point_id=:point and a.status<>'REMOVED'
            and coalesce(a.actual_employee_id,a.employee_id) is not null
            and s.starts_at<=:now and s.ends_at>:now
            order by a.id,e.updated_at desc,e.id
            """).setParameter("tenant",tenantId).setParameter("point",pointId).setParameter("now",now).getResultList();
        Map<UUID,Recipient> unique=new LinkedHashMap<>(); Set<UUID> warned=new HashSet<>();
        for(Object row:rows) {
            Object[] r=(Object[])row; UUID employee=UUID.fromString(r[0].toString()),assignment=UUID.fromString(r[1].toString());
            if(((Number)r[6]).intValue()>1 && warned.add(assignment)) Logger.getLogger(ObsintAgentRecipients.class).warnf("Más de un snapshot para empleado %s; se toma el más reciente",employee);
            unique.putIfAbsent(employee,new Recipient(employee,assignment,UUID.fromString(r[2].toString()),(String)r[3],r[4]==null?null:((Number)r[4]).longValue(),(String)r[5]));
        }
        return List.copyOf(unique.values());
    }
}
