package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.assignments.*;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.*;
import com.cajamarca.sgi.comando.security.AppUser;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;

/** Identidad del operador (usuario → empleado) y autorización de su asignación, compartida por los endpoints de SGI: Operador. */
@RequestScoped
public class OperatorContext {
    public record Assignment(OperationalAssignmentEntity assignment, ShiftOccurrenceEntity shift, PostEntity post, PointEntity point) {}

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject EntityManager em;
    @ConfigProperty(name="sgi.operator.relief-uat-enabled", defaultValue="false") boolean enabled;

    public String username() { return identity.getPrincipal().getName(); }

    public AppUser actor() {
        if(!enabled) throw new NotFoundException("Integración de relevo UAT deshabilitada");
        if(com.cajamarca.sgi.comando.security.IdentIdentity.isIdent(identity)) return null; // IDENT ya autenticó; el Rol viene del token
        AppUser u=AppUser.find("username=?1 and instanceCountryId=?2",username(),tenant.instanceCountryId()).firstResult();
        if(u==null || !u.active) throw new ForbiddenException("Usuario no habilitado en esta instancia");
        return u;
    }

    public UUID employee() {
        AppUser u=actor();
        if(!identity.hasRole("AGENTE_SEGURIDAD") && !identity.hasRole("SUPERVISOR_SEGURIDAD")) throw new ForbiddenException("Rol de operador requerido");
        if(com.cajamarca.sgi.comando.security.IdentIdentity.isIdent(identity)) {
            // Login con IDENT: el empleado se encuentra por el persona_id de DHO que envia IDENT (V29), no por app_user.
            Long personaId=com.cajamarca.sgi.comando.security.IdentIdentity.personaId(identity);
            if(personaId==null) throw new ForbiddenException("IDENT aún no envía el persona_id de DHO de esta persona");
            List<?> byPersona=em.createNativeQuery("select employee_id from employee_operational_snapshot where instance_country_id=:tenant and persona_id=:persona")
                .setParameter("tenant",tenant.instanceCountryId()).setParameter("persona",personaId).getResultList();
            if(byPersona.size()!=1) throw new ForbiddenException("Vínculo persona empleado pendiente de configuración");
            return UUID.fromString(byPersona.get(0).toString());
        }
        List<?> rows=em.createNativeQuery("select employee_id from operator_employee_binding where instance_country_id=:tenant and username=:user and active=true")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("user",u.username).getResultList();
        if(rows.size()!=1) throw new ForbiddenException("Vínculo usuario empleado pendiente de configuración");
        return UUID.fromString(rows.get(0).toString());
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
