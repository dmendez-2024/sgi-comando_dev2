package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.assignments.*;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.*;
import com.cajamarca.sgi.comando.security.AppUser;
import com.cajamarca.sgi.comando.security.IdentIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import java.util.*;

/** Identidad del operador (usuario → empleado) y autorización de su asignación, compartida por los endpoints de SGI: Operador. */
@RequestScoped
public class OperatorContext {
    public record Assignment(OperationalAssignmentEntity assignment, ShiftOccurrenceEntity shift, PostEntity post, PointEntity point) {}

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject EntityManager em;
    @Inject OperatorEmployeeLookup employees;

    public String username() { return identity.getPrincipal().getName(); }

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
