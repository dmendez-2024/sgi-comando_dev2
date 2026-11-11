package com.cajamarca.sgi.comando.smc;

import com.cajamarca.sgi.comando.context.CoreInstanceCountrySnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;

/** Acreditación administrativa explícita; nunca deduce un franco de un hueco del calendario. */
@ApplicationScoped
public class DayOffWorkedService {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    public record Request(UUID assignmentId, LocalDate restDate, String restEvidenceRef,
                          String workEvidenceRef, String reason) {}
    public record Receipt(UUID id,String status,boolean duplicate) {}

    @Transactional
    public Receipt accredit(UUID tenant, String actor, Request request) {
        if(request==null || request.assignmentId()==null || request.restDate()==null)
            throw new BadRequestException("assignmentId y restDate son obligatorios");
        String rest=required(request.restEvidenceRef(),500,"restEvidenceRef");
        String work=required(request.workEvidenceRef(),500,"workEvidenceRef");
        String reason=required(request.reason(),2000,"reason");
        actor=required(actor,160,"aprobador");
        String hash=hash(List.of(request.assignmentId().toString(),request.restDate().toString(),rest,work,reason));
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key,0))")
            .setParameter("key",tenant+":"+request.assignmentId()).getSingleResult();
        // Serializar con el ingreso de Operador y bloquear la fila ante reasignaciones.
        List<?> rows=em.createNativeQuery("""
            select coalesce(a.actual_employee_id,a.employee_id),a.status,ap.status,s.starts_at,s.ends_at,
                   p.point_id,s.post_id,s.id
            from operational_assignment a
            join assignment_plan ap on ap.id=a.assignment_plan_id and ap.instance_country_id=a.instance_country_id
            join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
            left join post p on p.id=s.post_id and p.instance_country_id=s.instance_country_id
            where a.id=:assignment and a.instance_country_id=:tenant for update of a
            """).setParameter("assignment",request.assignmentId()).setParameter("tenant",tenant).getResultList();
        if(rows.isEmpty())throw new NotFoundException("Asignación no encontrada en la instancia");
        List<?> previous=em.createNativeQuery("select id,payload_hash from day_off_worked_accreditation where instance_country_id=:tenant and assignment_id=:assignment")
            .setParameter("tenant",tenant).setParameter("assignment",request.assignmentId()).getResultList();
        if(!previous.isEmpty()) {
            Object[] old=(Object[])previous.getFirst();
            if(!hash.equals(old[1]))throw new ClientErrorException("La asignación ya tiene una acreditación diferente",409);
            return new Receipt(SmcFactSources.uuid(old[0]),"ACCREDITED",true);
        }
        Object[] row=(Object[])rows.getFirst();
        UUID employee=SmcFactSources.uuid(row[0]);
        if(employee==null || "REMOVED".equals(row[1]) || !Set.of("PUBLISHED","CLOSED").contains(row[2]))
            throw new BadRequestException("Se requiere una asignación publicada con agente efectivo");
        Instant start=SmcFactSources.instant(row[3]),end=SmcFactSources.instant(row[4]);
        if(!start.isBefore(end) || end.isAfter(Instant.now()))
            throw new BadRequestException("El turno debe haber finalizado");
        ZoneId zone=ZoneId.of("America/Guayaquil");
        CoreInstanceCountrySnapshot country=CoreInstanceCountrySnapshot.find("instanceCountryId=?1",tenant).firstResult();
        if(country!=null && country.timezone!=null && !country.timezone.isBlank()) {
            try { zone=ZoneId.of(country.timezone); }
            catch(DateTimeException e) { throw new BadRequestException("Zona horaria de instancia inválida"); }
        }
        if(!request.restDate().equals(start.atZone(zone).toLocalDate()))
            throw new BadRequestException("restDate debe coincidir con la fecha local de inicio del turno");
        UUID id=UUID.nameUUIDFromBytes(("DAY_OFF_WORKED:"+tenant+":"+employee+":"+request.restDate()).getBytes(StandardCharsets.UTF_8));
        int added=em.createNativeQuery("""
            insert into day_off_worked_accreditation(id,instance_country_id,assignment_id,employee_id,rest_date,
                occurred_at,point_id,post_id,shift_id,rest_evidence_ref,work_evidence_ref,reason,accredited_by,payload_hash)
            values(:id,:tenant,:assignment,:employee,:date,:at,:point,:post,:shift,:rest,:work,:reason,:actor,:hash)
            on conflict (instance_country_id,employee_id,rest_date) do nothing
            """).setParameter("id",id).setParameter("tenant",tenant).setParameter("assignment",request.assignmentId())
            .setParameter("employee",employee).setParameter("date",request.restDate()).setParameter("at",end)
            .setParameter("point",SmcFactSources.uuid(row[5])).setParameter("post",SmcFactSources.uuid(row[6]))
            .setParameter("shift",SmcFactSources.uuid(row[7])).setParameter("rest",rest).setParameter("work",work)
            .setParameter("reason",reason).setParameter("actor",actor).setParameter("hash",hash).executeUpdate();
        if(added==0) {
            Object[] prior=(Object[])em.createNativeQuery("""
                select id,payload_hash from day_off_worked_accreditation
                where instance_country_id=:tenant and employee_id=:employee and rest_date=:date
                """).setParameter("tenant",tenant).setParameter("employee",employee).setParameter("date",request.restDate()).getSingleResult();
            if(!hash.equals(prior[1]))throw new ClientErrorException("El franco ya fue acreditado con contenido diferente",409);
            id=SmcFactSources.uuid(prior[0]);
        }
        return new Receipt(id,"ACCREDITED",added==0);
    }

    public Map<String,Object> get(UUID tenant,UUID id) {
        List<?> rows=em.createNativeQuery("""
            select id,assignment_id,employee_id,rest_date,occurred_at,point_id,post_id,shift_id,
                   rest_evidence_ref,work_evidence_ref,reason,accredited_by,accredited_at
            from day_off_worked_accreditation where id=:id and instance_country_id=:tenant
            """).setParameter("id",id).setParameter("tenant",tenant).getResultList();
        if(rows.isEmpty())throw new NotFoundException("Acreditación no encontrada");
        String[] keys={"id","assignmentId","employeeId","restDate","occurredAt","pointId","postId","shiftId",
            "restEvidenceRef","workEvidenceRef","reason","accreditedBy","accreditedAt"};
        Object[] row=(Object[])rows.getFirst();Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<keys.length;i++)result.put(keys[i],row[i]==null?null:row[i].toString());
        result.put("status","ACCREDITED");return result;
    }

    private static String required(String value,int max,String field) {
        if(value==null || value.isBlank() || value.trim().length()>max)
            throw new BadRequestException(field+" es obligatorio y admite máximo "+max+" caracteres");
        return value.trim();
    }
    private String hash(List<String> content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(content))); }
        catch(Exception e) { throw new IllegalStateException("No se pudo identificar la acreditación",e); }
    }
}
