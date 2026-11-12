package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.assignments.*;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.*;
import com.cajamarca.sgi.comando.security.AppUser;
import com.cajamarca.sgi.comando.security.IdentIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Identidad del operador (usuario → empleado) y autorización de su asignación, compartida por los endpoints de SGI: Operador. */
@RequestScoped
public class OperatorContext {
    public record Assignment(OperationalAssignmentEntity assignment, ShiftOccurrenceEntity shift, PostEntity post, PointEntity point) {}

    /**
     * Autor de un envío. Normalmente es quien inició sesión. Si la app sincroniza registros que otro agente dejó
     * pendientes en el dispositivo del punto ({@link #RELAY_HEADER}), el autor es el dueño de la asignación y
     * {@code relayedBy*} indica quién los sincronizó.
     */
    public record Author(UUID employee, String username, UUID relayedByEmployee, String relayedByUsername) {
        public boolean relayed() { return relayedByEmployee!=null; }
    }

    /** La app envía registros pendientes de otro agente (un solo dispositivo por punto). */
    public static final String RELAY_HEADER = "X-Sgi-Sync-Relay";
    /** Usuario IDENT ({@code sub}) del agente que hizo el registro, si la app lo conoce. */
    public static final String RELAY_AUTHOR_HEADER = "X-Sgi-Sync-Author";
    /** Antigüedad máxima del turno del autor para aceptar sus registros desde otro agente. */
    static final Duration RELAY_MAX_AGE = Duration.ofDays(7);

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject EntityManager em;
    @Inject OperatorEmployeeLookup employees;
    @Inject CurrentVertxRequest request;

    private Author author;

    /** Usuario que queda como autor: el de la sesión o, en un envío por otro agente ya resuelto con {@link #author}, el del dueño. */
    public String username() { return author!=null ? author.username() : identity.getPrincipal().getName(); }

    /** Autor resuelto en esta petición; null si el endpoint aún no llamó a {@link #author}. */
    public Author currentAuthor() { return author; }

    /**
     * Empleado que queda como autor de un envío sobre {@code assignmentId}. Sin {@link #RELAY_HEADER} es siempre
     * {@code caller}. Con la cabecera y una asignación de otro agente, la acepta solo si:
     * el turno de esa asignación ya empezó y terminó hace {@link #RELAY_MAX_AGE} o menos, y quien envía tiene un turno
     * vigente (o por empezar en {@link OperatorAssignmentWindow#EARLY_ENTRY}) en el mismo punto o puesto de coordinación.
     * Cada envío aceptado así queda en {@code operator_sync_relay} ({@code operation} + {@code reference}).
     */
    public UUID author(UUID caller,UUID assignmentId,String operation,Object reference) {
        String principal=identity.getPrincipal().getName();
        author=new Author(caller,principal,null,null);
        if(!relayRequested()) return caller;
        OperationalAssignmentEntity a=OperationalAssignmentEntity.find("id=?1 and instanceCountryId=?2",assignmentId,tenant.instanceCountryId()).firstResult();
        if(a==null || "REMOVED".equals(a.status) || a.effectiveEmployeeId()==null) throw new ForbiddenException("Asignación no autorizada");
        UUID owner=a.effectiveEmployeeId();
        if(owner.equals(caller)) return caller;
        ShiftOccurrenceEntity s=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",a.shiftOccurrenceId,tenant.instanceCountryId()).firstResult();
        if(s==null) throw new NotFoundException("Turno no disponible");
        Instant now=Instant.now();
        if(now.isBefore(s.startsAt) || s.endsAt.isBefore(now.minus(RELAY_MAX_AGE)))
            throw new ForbiddenException("Solo se sincronizan registros de otro agente de turnos de los últimos "+RELAY_MAX_AGE.toDays()+" días");
        if(!onDutyAtSamePlace(caller,s,now))
            throw new ForbiddenException("Solo un agente con turno vigente en el mismo punto puede sincronizar registros de otro agente");
        String declared=Optional.ofNullable(header(RELAY_AUTHOR_HEADER)).map(String::trim).filter(v->!v.isEmpty() && v.length()<=160).orElse(null);
        author=new Author(owner,declared!=null?declared:fallbackUsername(owner),caller,principal);
        em.createNativeQuery("""
          insert into operator_sync_relay(instance_country_id,operation,reference_id,assignment_id,author_employee_id,author_username,relayed_by_employee_id,relayed_by_username,relayed_at)
          values(:t,:operation,:reference,:assignment,:author,:authorName,:relayer,:relayerName,current_timestamp)
          on conflict (instance_country_id,operation,reference_id) do nothing
          """).setParameter("t",tenant.instanceCountryId()).setParameter("operation",operation).setParameter("reference",String.valueOf(reference))
            .setParameter("assignment",assignmentId).setParameter("author",owner).setParameter("authorName",author.username())
            .setParameter("relayer",caller).setParameter("relayerName",principal).executeUpdate();
        return owner;
    }

    /**
     * Empleado que el lote debe declarar: el autor o, en un envío por otro agente, también quien sincroniza (la app no
     * siempre conoce el empleado del autor). Usar después de {@link #author}.
     */
    public UUID batchEmployee(String declared) {
        if(author==null) throw new IllegalStateException("Resolver el autor antes de validar el lote");
        if(author.relayed() && author.relayedByEmployee().toString().equalsIgnoreCase(Objects.toString(declared,"").trim())) return author.relayedByEmployee();
        return author.employee();
    }

    private boolean relayRequested() {
        String v=header(RELAY_HEADER);
        return v!=null && ("true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()));
    }

    private String header(String name) {
        var current=request==null?null:request.getCurrent();
        return current==null?null:current.request().getHeader(name);
    }

    /** Quien sincroniza tiene un turno publicado vigente (o por empezar) en el mismo punto o puesto de coordinación. */
    private boolean onDutyAtSamePlace(UUID caller,ShiftOccurrenceEntity target,Instant now) {
        UUID point=null;
        if(target.postId!=null) {
            PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",target.postId,tenant.instanceCountryId()).firstResult();
            if(post!=null) point=post.pointId;
        }
        if(point==null && target.coordinationPostId==null) return false;
        String place=point!=null ? "po.point_id=:place" : "s.coordination_post_id=:place";
        return !em.createNativeQuery("""
          select 1 from operational_assignment a
          join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
          join assignment_plan p on p.id=a.assignment_plan_id and p.instance_country_id=a.instance_country_id
          left join post po on po.id=s.post_id and po.instance_country_id=s.instance_country_id
          where a.instance_country_id=:t and a.status<>'REMOVED' and p.status in ('PUBLISHED','CLOSED')
            and coalesce(a.actual_employee_id,a.employee_id)=:caller
            and s.ends_at>:now and s.starts_at<=:latestStart
            and\s""" + place).setParameter("t",tenant.instanceCountryId()).setParameter("caller",caller).setParameter("now",now)
            .setParameter("latestStart",now.plus(OperatorAssignmentWindow.EARLY_ENTRY))
            .setParameter("place",point!=null?point:target.coordinationPostId)
            .setMaxResults(1).getResultList().isEmpty();
    }

    /** Sin el usuario IDENT del autor queda su persona de DHO (mismo formato que el ingreso por persona_id). */
    private String fallbackUsername(UUID employee) {
        List<?> rows=em.createNativeQuery("select persona_id from employee_operational_snapshot where instance_country_id=:t and employee_id=:e")
            .setParameter("t",tenant.instanceCountryId()).setParameter("e",employee).setMaxResults(1).getResultList();
        return rows.isEmpty() || rows.get(0)==null ? "employee:"+employee : "persona:"+rows.get(0);
    }

    /** Persona habilitada para la API del operador. Con IDENT el token ya lo dice; con usuario y contrasena de SGI solo
     * entra la web (los Roles de SGI Operador los rechaza IdentAccessFilter) y su app_user debe estar activo. */
    public void actor() {
        if(IdentIdentity.isIdent(identity)) return;
        AppUser u=AppUser.find("username=?1 and instanceCountryId=?2",username(),tenant.instanceCountryId()).firstResult();
        if(u==null || !u.active) throw new ForbiddenException("Usuario no habilitado en esta instancia");
    }

    /** Empleado del Agente o Supervisor de Seguridad: solo con IDENT, por su persona_id. */
    public UUID employee() {
        actor();
        if(!IdentIdentity.hasOperatorRole(identity)) throw new ForbiddenException("Rol de operador requerido");
        return employees.employeeOf(identity);
    }

    /** Agente de Seguridad (Rol AGENTE_SEGURIDAD del token). El Supervisor de Seguridad que no sea también Agente da false. */
    public boolean isAgent() {
        return identity!=null && identity.hasRole("AGENTE_SEGURIDAD");
    }

    public Assignment assignment(UUID id,UUID employee) {
        OperationalAssignmentEntity a=OperationalAssignmentEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();
        if(a==null || "REMOVED".equals(a.status) || !employee.equals(a.effectiveEmployeeId())) throw new ForbiddenException("Asignación no autorizada");
        ShiftOccurrenceEntity s=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",a.shiftOccurrenceId,tenant.instanceCountryId()).firstResult();
        if(s==null) throw new NotFoundException("Turno no disponible");
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",s.postId,tenant.instanceCountryId()).firstResult();
        PointEntity point=post==null?null:PointEntity.find("id=?1 and instanceCountryId=?2",post.pointId,tenant.instanceCountryId()).firstResult();
        if(point==null) throw new NotFoundException("Puesto o punto no disponible");
        return new Assignment(a,s,post,point);
    }

    /** Serializa en la transacción actual las operaciones sobre una misma asignación. */
    public void lock(UUID assignmentId) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key,0))").setParameter("key",tenant.instanceCountryId()+":"+assignmentId).getSingleResult();
    }
}
