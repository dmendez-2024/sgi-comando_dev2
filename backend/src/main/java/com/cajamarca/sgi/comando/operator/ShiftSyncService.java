package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.context.CoreInstanceCountrySnapshot;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** El reloj nunca cierra un turno: solo lo hace el manifiesto final confirmado del dispositivo. */
@ApplicationScoped
public class ShiftSyncService {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    record Window(Instant start,Instant end) {}
    record Assignment(UUID employee,UUID point,UUID post,UUID shift,Instant start,Instant end) {}

    public void ensureOpen(UUID tenant,UUID assignment) {
        lock(tenant,assignment);
        if(!em.createNativeQuery("select assignment_id from operator_shift_sync where instance_country_id=:t and assignment_id=:a and completed_at is not null")
            .setParameter("t",tenant).setParameter("a",assignment).getResultList().isEmpty())
            throw new ClientErrorException("El turno ya fue conciliado; una corrección requiere revisión",409);
    }
    void lock(UUID tenant,UUID assignment) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key,0))")
            .setParameter("key",tenant+":"+assignment).getSingleResult();
    }
    @Transactional
    public JsonNode submit(UUID tenant,UUID employee,String actor,JsonNode request) {
        if(request==null || !request.isObject())throw new BadRequestException("Cuerpo obligatorio");
        UUID assignment=ReliefContract.uuid(request,"assignmentId");
        String device=ReliefContract.text(request,"deviceId");
        if(device.length()>120)throw new BadRequestException("deviceId demasiado largo");
        String action=ReliefContract.text(request,"action");
        if(!Set.of("PREPARE","CHECK","COMPLETE").contains(action))throw new BadRequestException("Acción inválida");
        lock(tenant,assignment);
        Assignment a=assignment(tenant,assignment,employee);
        List<?> previous=em.createNativeQuery("select employee_id,device_id,expected_json,completed_at,manifest_hash,patrols_not_executed,consignments_not_done from operator_shift_sync where instance_country_id=:t and assignment_id=:a for update")
            .setParameter("t",tenant).setParameter("a",assignment).getResultList();
        Object[] saved=previous.isEmpty()?null:(Object[])previous.getFirst();
        if(saved!=null && (!employee.toString().equals(saved[0].toString()) || !device.equals(saved[1])))
            throw new ClientErrorException("El turno está preparado por otro agente o dispositivo",409);
        if("PREPARE".equals(action)) {
            if(saved!=null)return receipt(assignment,saved[3]==null?"PREPARED":"CLOSED",true,((Number)saved[5]).intValue(),((Number)saved[6]).intValue());
            if(!Instant.now().isBefore(a.end()))throw new ClientErrorException("No hay configuración preparada antes del fin del turno; requiere revisión",409);
            ArrayNode expected=expected(tenant,a);
            em.createNativeQuery("insert into operator_shift_sync(instance_country_id,assignment_id,employee_id,device_id,expected_json) values(:t,:a,:e,:d,:json)")
                .setParameter("t",tenant).setParameter("a",assignment).setParameter("e",employee).setParameter("d",device).setParameter("json",expected.toString()).executeUpdate();
            return receipt(assignment,"PREPARED",false,0,0);
        }
        if(saved==null)throw new ClientErrorException("Turno sin preparación; no se infieren incumplimientos",409);
        if(Instant.now().isBefore(a.end()))throw new ClientErrorException("El turno aún no termina",409);
        if(!request.path("allAcknowledged").isBoolean() || !request.path("allAcknowledged").booleanValue())
            throw new ClientErrorException("La sincronización no está completa",409);
        ArrayNode operations=canonicalOperations(request.path("operations"));
        String hash=digest(operations.toString());
        if(saved[3]!=null) {
            if(!hash.equals(saved[4]))throw new ClientErrorException("El turno ya cerró con otro manifiesto",409);
            return receipt(assignment,"CLOSED",true,((Number)saved[5]).intValue(),((Number)saved[6]).intValue());
        }
        ArrayNode expected=readArray(saved[2].toString());
        if(!expected.equals(expected(tenant,a)))throw new ClientErrorException("Cambió la configuración del turno; requiere revisión",409);
        for(JsonNode operation:operations)verify(tenant,assignment,employee,operation);
        if(((Number)em.createNativeQuery("select count(*) from patrol_execution where instance_country_id=:t and assignment_id=:a and finished_at is null")
            .setParameter("t",tenant).setParameter("a",assignment).getSingleResult()).longValue()>0)
            throw new ClientErrorException("Hay una patrulla iniciada sin cierre; requiere revisión",409);
        if("CHECK".equals(action))return receipt(assignment,"READY",false,0,0);
        int patrols=0,consignments=0;
        for(JsonNode item:expected) {
            if("CONTEXT".equals(item.path("kind").asText()))continue;
            UUID target=UUID.fromString(item.path("id").asText());
            if("CONSIGNMENT".equals(item.path("kind").asText())) {
                UUID id=stable(tenant+":"+assignment+":CONSIGNMENT:"+target);
                String payload=mapper.createObjectNode().put("source","SHIFT_SYNC_COMPLETION").put("assignmentId",assignment.toString()).put("consignmentId",target.toString()).toString();
                consignments+=em.createNativeQuery("""
                    insert into consignment_compliance(id,instance_country_id,protocol_id,consignment_id,assignment_id,
                        employee_id,username,result,comment,photo_count,gps_required,payload_hash,payload_json,confirmed_at,created_at,occurred_at)
                    select :id,:t,:protocol,:target,:a,:e,:actor,'NO_CUMPLIDA',
                        'No realizada al conciliar el turno completamente sincronizado',0,false,:hash,:json,current_timestamp,current_timestamp,:at
                    where not exists(select 1 from consignment_compliance where instance_country_id=:t and assignment_id=:a and consignment_id=:target)
                    on conflict do nothing
                    """).setParameter("id",id).setParameter("t",tenant).setParameter("protocol",UUID.fromString(item.path("protocolId").asText()))
                    .setParameter("target",target).setParameter("a",assignment).setParameter("e",employee).setParameter("actor",actor)
                    .setParameter("hash",digest(payload)).setParameter("json",payload).setParameter("at",a.end()).executeUpdate();
            } else {
                Instant from=Instant.parse(item.path("from").asText()),to=Instant.parse(item.path("to").asText());
                UUID id=stable(tenant+":"+assignment+":PATROL:"+target+":"+from);
                patrols+=em.createNativeQuery("""
                    insert into operator_shift_patrol_omission(id,instance_country_id,assignment_id,employee_id,
                        patrol_definition_id,point_id,post_id,shift_id,window_start,window_end)
                    select :id,:t,:a,:e,:target,:point,:post,:shift,:from,:to
                    where not exists(select 1 from patrol_execution pe
                        left join patrol_plan p on p.id=pe.patrol_plan_id and p.instance_country_id=pe.instance_country_id
                        where pe.instance_country_id=:t and pe.assignment_id=:a
                          and coalesce(pe.patrol_definition_id,p.patrol_definition_id)=:target
                          and pe.started_at>=:from and pe.started_at<:to)
                    on conflict do nothing
                    """).setParameter("id",id).setParameter("t",tenant).setParameter("a",assignment).setParameter("e",employee)
                    .setParameter("target",target).setParameter("point",a.point()).setParameter("post",a.post()).setParameter("shift",a.shift())
                    .setParameter("from",from).setParameter("to",to).executeUpdate();
            }
        }
        em.createNativeQuery("update operator_shift_sync set completed_at=current_timestamp,manifest_hash=:hash,manifest_json=:json,patrols_not_executed=:p,consignments_not_done=:c where instance_country_id=:t and assignment_id=:a")
            .setParameter("hash",hash).setParameter("json",operations.toString()).setParameter("p",patrols).setParameter("c",consignments)
            .setParameter("t",tenant).setParameter("a",assignment).executeUpdate();
        return receipt(assignment,"CLOSED",false,patrols,consignments);
    }
    Assignment assignment(UUID tenant,UUID id,UUID employee) {
        List<?> rows=em.createNativeQuery("""
            select coalesce(a.actual_employee_id,a.employee_id),p.point_id,s.post_id,s.id,s.starts_at,s.ends_at
            from operational_assignment a join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
            join assignment_plan ap on ap.id=a.assignment_plan_id and ap.instance_country_id=a.instance_country_id
            join post p on p.id=s.post_id and p.instance_country_id=s.instance_country_id
            where a.instance_country_id=:t and a.id=:id and a.status<>'REMOVED' and ap.status in ('PUBLISHED','CLOSED') for update of a
            """).setParameter("t",tenant).setParameter("id",id).getResultList();
        if(rows.size()!=1)throw new ForbiddenException("Asignación no autorizada");
        Object[] r=(Object[])rows.getFirst();
        if(r[0]==null || !employee.toString().equals(r[0].toString()))throw new ForbiddenException("Agente incorrecto");
        return new Assignment(employee,uuid(r[1]),uuid(r[2]),uuid(r[3]),time(r[4]),time(r[5]));
    }
    ArrayNode expected(UUID tenant,Assignment a) {
        ZoneId zone=ZoneId.of("America/Guayaquil");
        CoreInstanceCountrySnapshot country=CoreInstanceCountrySnapshot.find("instanceCountryId",tenant).firstResult();
        if(country!=null && country.timezone!=null)zone=ZoneId.of(country.timezone);
        ArrayNode out=mapper.createArrayNode();
        out.addObject().put("kind","CONTEXT").put("id",a.shift().toString()).put("employeeId",a.employee().toString())
            .put("postId",a.post().toString()).put("startsAt",a.start().toString()).put("endsAt",a.end().toString());
        List<?> consignments=em.createNativeQuery("""
            select c.id,c.protocol_id,c.updated_at,p.updated_at,c.application_type,c.application_days_json,
                   c.application_time_from,c.application_time_to,c.validity_from,c.validity_until
            from consignment c join consignment_protocol p on p.id=c.protocol_id and p.instance_country_id=c.instance_country_id
            where c.instance_country_id=:t and c.point_id=:point and c.status='VIGENTE' and p.status='ACTIVO'
              and (c.scope_type='POINT' or c.post_id=:post or exists(select 1 from consignment_post_scope s
                where s.instance_country_id=c.instance_country_id and s.consignment_id=c.id and s.post_id=:post)) order by c.id
            """).setParameter("t",tenant).setParameter("point",a.point()).setParameter("post",a.post()).getResultList();
        for(Object raw:consignments) { Object[] r=(Object[])raw;
            Instant from=r[8]==null?a.start():max(a.start(),time(r[8]));
            Instant to=r[9]==null?a.end():min(a.end(),time(r[9]));
            if(!from.isBefore(to))continue;
            boolean applicable=!"CALENDAR".equals(r[4]);
            if(!applicable && r[6]!=null && r[7]!=null) {
                Set<String> calendar=days(r[5].toString());
                applicable=!calendar.isEmpty()&&!windows(from,to,LocalTime.parse(r[6].toString()),LocalTime.parse(r[7].toString()),calendar,zone,false).isEmpty();
            }
            if(applicable)out.addObject().put("kind","CONSIGNMENT").put("id",r[0].toString()).put("protocolId",r[1].toString())
                .put("version",time(r[2])+":"+time(r[3]));
        }
        List<?> patrols=em.createNativeQuery("""
            select d.id,d.protocol_id,d.updated_at,p.updated_at,d.window_start,d.window_end
            from patrol_definition d join patrol_protocol p on p.id=d.protocol_id and p.instance_country_id=d.instance_country_id
            where d.instance_country_id=:t and d.status='ACTIVO' and p.status='ACTIVO'
              and d.structure_type='CLOSED' and d.schedule_type='PROGRAMMED'
              and exists(select 1 from patrol_protocol_post_scope s where s.instance_country_id=p.instance_country_id and s.protocol_id=p.id and s.post_id=:post)
            order by d.id
            """).setParameter("t",tenant).setParameter("post",a.post()).getResultList();
        for(Object raw:patrols) { Object[] r=(Object[])raw;
            if(r[4]==null || r[5]==null)throw new ClientErrorException("Patrulla programada sin ventana",409);
            for(Window w:windows(a.start(),a.end(),LocalTime.parse(r[4].toString()),LocalTime.parse(r[5].toString()),Set.of(),zone,true))
                out.addObject().put("kind","PATROL").put("id",r[0].toString()).put("protocolId",r[1].toString())
                    .put("version",time(r[2])+":"+time(r[3])).put("from",w.start().toString()).put("to",w.end().toString());
        }
        return out;
    }
    Set<String> days(String json) { Set<String> days=new HashSet<>();for(JsonNode n:readArray(json))days.add(n.asText().toUpperCase(Locale.ROOT));return days; }
    static List<Window> windows(Instant start,Instant end,LocalTime from,LocalTime to,Set<String> days,ZoneId zone,boolean whole) {
        List<Window> out=new ArrayList<>();
        if(!start.isBefore(end)||from.equals(to))return out;
        for(LocalDate d=start.atZone(zone).toLocalDate().minusDays(1);!d.isAfter(end.atZone(zone).toLocalDate());d=d.plusDays(1)) {
            if(!days.isEmpty()&&!days.contains(d.getDayOfWeek().name().substring(0,3)))continue;
            Instant s=d.atTime(from).atZone(zone).toInstant(),e=(to.isAfter(from)?d:d.plusDays(1)).atTime(to).atZone(zone).toInstant();
            if(whole ? !s.isBefore(start)&&!e.isAfter(end) : s.isBefore(end)&&e.isAfter(start))out.add(new Window(max(start,s),min(end,e)));
        }
        return out;
    }
    ArrayNode canonicalOperations(JsonNode input) {
        if(!input.isArray() || input.size()>10000)throw new BadRequestException("operations debe ser un arreglo de hasta 10000 registros");
        TreeMap<String,JsonNode> sorted=new TreeMap<>();
        for(JsonNode n:input) {
            String kind=ReliefContract.text(n,"kind"),id=ReliefContract.uuid(n,"id").toString(),action=n.path("action").asText("");
            int photos=n.path("photoCount").asInt(0);if(photos<0 || photos>100)throw new BadRequestException("photoCount inválido");
            ObjectNode value=mapper.createObjectNode().put("kind",kind).put("id",id).put("action",action).put("photoCount",photos);
            if(sorted.put(kind+":"+id+":"+action,value)!=null)throw new BadRequestException("Operación duplicada en el manifiesto");
        }
        ArrayNode out=mapper.createArrayNode();sorted.values().forEach(out::add);return out;
    }
    void verify(UUID tenant,UUID assignment,UUID employee,JsonNode item) {
        String kind=item.path("kind").asText(),extra="";
        String table=switch(kind) {
            case "PATRULLAS" -> "patrol_execution";
            case "CONSIGNAS" -> "consignment_compliance";
            case "PATRULLAS_HITO","BITACORA_FOTOS","EVIDENCE" -> "task_execution";
            case "RELEVO" -> "operator_relief_submission";
            case "BITACORA","BITACORA_SALIDA" -> "operator_logbook_record";
            case "INCIDENTES","HALLAZGOS","VULNERABILIDADES" -> "operator_operational_report";
            case "CONSIGNA_REQUEST" -> "operator_consignment_review_request";
            case "FILE" -> "evidence_object";
            default -> throw new BadRequestException("Tipo de operación desconocido");
        };
        if("PATRULLAS".equals(kind)) {
            String action=item.path("action").asText();
            if(!Set.of("START","FINISH").contains(action))throw new BadRequestException("Acción de patrulla inválida");
            extra="FINISH".equals(action)?" and finished_at is not null":" and started_at is not null";
        }
        if("FILE".equals(kind))extra=" and status in ('STORED','ATTACHED')";
        String column="BITACORA_SALIDA".equals(kind)?"exit_operation_id":"FILE".equals(kind)?"event_id":"id";
        if(((Number)em.createNativeQuery("select count(*) from "+table+" where instance_country_id=:t and assignment_id=:a and employee_id=:e and "+column+"=:id"+extra)
            .setParameter("t",tenant).setParameter("a",assignment).setParameter("e",employee).setParameter("id",UUID.fromString(item.path("id").asText())).getSingleResult()).longValue()!=1)
            throw new ClientErrorException("Comando no confirmó todos los registros del manifiesto",409);
        if(Set.of("PATRULLAS_HITO","BITACORA_FOTOS","EVIDENCE").contains(kind)) {
            if(((Number)em.createNativeQuery("select count(*) from task_execution_evidence x join evidence_object e on e.id=x.evidence_id where x.task_execution_id=:id and e.instance_country_id=:t and e.assignment_id=:a and e.status='ATTACHED'")
                .setParameter("id",UUID.fromString(item.path("id").asText())).setParameter("t",tenant).setParameter("a",assignment).getSingleResult()).longValue()<1)
                throw new ClientErrorException("Falta evidencia adjunta de una tarea",409);
        }
    }
    ObjectNode receipt(UUID assignment,String status,boolean duplicate,int patrols,int consignments) {
        return mapper.createObjectNode().put("assignmentId",assignment.toString()).put("status",status).put("duplicate",duplicate)
            .put("patrolsNotExecuted",patrols).put("consignmentsNotDone",consignments);
    }
    ArrayNode readArray(String json) {try {JsonNode n=mapper.readTree(json);if(n.isArray())return (ArrayNode)n;}catch(Exception ignored){}throw new BadRequestException("Arreglo inválido");}
    static UUID stable(String s) {return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));}
    static Instant min(Instant a,Instant b){return a.isBefore(b)?a:b;}
    static Instant max(Instant a,Instant b){return a.isAfter(b)?a:b;}
    static UUID uuid(Object v){return UUID.fromString(v.toString());}
    static Instant time(Object v){if(v instanceof Instant i)return i;if(v instanceof java.sql.Timestamp t)return t.toInstant();if(v instanceof OffsetDateTime d)return d.toInstant();return Instant.parse(v.toString());}
    static String digest(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
