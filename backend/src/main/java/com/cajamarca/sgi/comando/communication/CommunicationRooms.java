package com.cajamarca.sgi.comando.communication;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

/** Salas de Comunicación. La sala "Monitor Consola" recibe todos los mensajes de la Consola OBSINT. */
@ApplicationScoped
public class CommunicationRooms {
    public static final String MONITOR_CONSOLE_CODE = "MONITOR_CONSOLA";
    public static final String MONITOR_CONSOLE_NAME = "Monitor Consola";

    /**
     * Sala por defecto de la instancia para los mensajes de la Consola OBSINT. La crea la migración V125; si falta (otra
     * instancia o base nueva) se crea aquí, sin duplicarla aunque dos envíos coincidan.
     */
    public UUID monitorConsole(UUID instanceCountryId) {
        UUID existing = find(instanceCountryId);
        if (existing != null) return existing;
        CommunicationRoom.getEntityManager().createNativeQuery("""
            insert into communication_room(id,instance_country_id,code,name,source,is_default,active,created_at,updated_at)
            values (:id,:tenant,:code,:name,:source,true,true,now(),now())
            on conflict do nothing""")
            .setParameter("id", UUID.randomUUID()).setParameter("tenant", instanceCountryId)
            .setParameter("code", MONITOR_CONSOLE_CODE).setParameter("name", MONITOR_CONSOLE_NAME)
            .setParameter("source", CommunicationRoom.SOURCE_OBSINT_CONSOLE).executeUpdate();
        UUID created = find(instanceCountryId);
        if (created == null) throw new IllegalStateException("No fue posible crear la sala Monitor Consola.");
        return created;
    }

    /** La sala MONITOR_CONSOLA; si otra sala quedó como predeterminada antes, esa. */
    private static UUID find(UUID instanceCountryId) {
        List<?> rows = CommunicationRoom.getEntityManager().createNativeQuery("""
            select id from communication_room where instance_country_id=:tenant and (code=:code or is_default)
            order by case when code=:code then 0 else 1 end limit 1""")
            .setParameter("tenant", instanceCountryId).setParameter("code", MONITOR_CONSOLE_CODE).getResultList();
        if (rows.isEmpty()) return null;
        Object id = rows.getFirst();
        return id instanceof UUID uuid ? uuid : UUID.fromString(id.toString());
    }
}
