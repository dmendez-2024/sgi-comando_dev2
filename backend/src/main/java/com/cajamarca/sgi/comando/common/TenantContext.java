package com.cajamarca.sgi.comando.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class TenantContext {
    @Inject EntityManager entityManager;

    private volatile UUID cachedInstanceCountryId;

    /**
     * El contexto vigente se conserva en base de datos y procede del catálogo
     * Instancia-País de CORE. No se acepta desde el navegador ni desde un UUID
     * fijo de configuración.
     */
    public UUID instanceCountryId() {
        UUID cached = cachedInstanceCountryId;
        if (cached != null) return cached;
        synchronized (this) {
            if (cachedInstanceCountryId == null) {
                Object value = entityManager.createNativeQuery(
                    "select instance_country_id from instance_country_context where singleton_key=1"
                ).getSingleResult();
                cachedInstanceCountryId = UUID.fromString(value.toString());
            }
            return cachedInstanceCountryId;
        }
    }

    /**
     * Migra atómicamente el alcance local al identificador canónico resuelto
     * desde CORE. La función SQL valida previamente que no haya mezcla de
     * tenants ni colisiones, y registra el cambio en su historial.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public synchronized int adoptCanonicalInstanceCountry(
        UUID canonicalInstanceCountryId,
        UUID countryId,
        String countryIsoAlpha2,
        String countryName,
        String instanceCountryCode,
        String instanceCountryName
    ) {
        Object result = entityManager.createNativeQuery("""
            select sgi_migrate_instance_country_context(
                cast(?1 as uuid), cast(?2 as uuid), ?3, ?4, ?5, ?6
            )
            """)
            .setParameter(1, canonicalInstanceCountryId)
            .setParameter(2, countryId)
            .setParameter(3, countryIsoAlpha2)
            .setParameter(4, countryName)
            .setParameter(5, instanceCountryCode)
            .setParameter(6, instanceCountryName)
            .getSingleResult();
        entityManager.clear();
        cachedInstanceCountryId = canonicalInstanceCountryId;
        return ((Number) result).intValue();
    }
}
