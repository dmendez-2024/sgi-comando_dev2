package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="user_operational_scope")
public class UserOperationalScope extends BaseEntity {
    @Column(nullable=false) public String username;
    @Column(name="scope_type", nullable=false) public String scopeType;
    @Column(name="scope_id") public UUID scopeId;
    /** persona_id de DHO (login con IDENT). */
    @Column(name="persona_id") public Long personaId;

    /** Alcances de la persona: por persona_id si entro con IDENT, por usuario si entro con usuario y contrasena de SGI. */
    public static java.util.List<UserOperationalScope> of(io.quarkus.security.identity.SecurityIdentity identity, UUID instanceCountryId) {
        if (com.cajamarca.sgi.comando.security.IdentIdentity.isIdent(identity)) {
            Long personaId = com.cajamarca.sgi.comando.security.IdentIdentity.personaId(identity);
            return personaId == null ? java.util.List.of() : UserOperationalScope.list("instanceCountryId=?1 and personaId=?2", instanceCountryId, personaId);
        }
        return UserOperationalScope.list("instanceCountryId=?1 and username=?2", instanceCountryId, identity.getPrincipal().getName());
    }
}
