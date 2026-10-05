package com.cajamarca.sgi.comando.settings;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.postconfig.PostOperationalConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Instant;
import java.util.*;

/**
 * Radio GPS (m) con que se compara la ubicación de las fotos del agente. Solo produce el aviso "Fuera del radio GPS"; nunca bloquea.
 * Resolución: radio propio de la tarea → predeterminado de la instancia (operational_setting) → sgi.evidence.default-radius-m (50).
 */
@ApplicationScoped
public class EvidenceLocationSettings {
    public static final String DEFAULT_RADIUS_KEY = "evidence.default_radius_m";
    public static final int MIN_RADIUS = 5, MAX_RADIUS = 5000;

    @Inject EntityManager em;
    @Inject TenantContext tenant;
    @ConfigProperty(name = "sgi.evidence.default-radius-m", defaultValue = "50") int fallbackRadius;

    /** Radio predeterminado de la instancia actual. */
    public int defaultRadius() { return defaultRadius(tenant.instanceCountryId()); }

    @SuppressWarnings("unchecked")
    public int defaultRadius(UUID instance) {
        List<Object> rows = em.createNativeQuery("select setting_value from operational_setting where instance_country_id=:t and setting_key=:k")
            .setParameter("t", instance).setParameter("k", DEFAULT_RADIUS_KEY).getResultList();
        if (rows.isEmpty()) return fallbackRadius;
        try { return Integer.parseInt(rows.get(0).toString().trim()); } catch (NumberFormatException e) { return fallbackRadius; }
    }

    /** Radio propio de la tarea si lo tiene; si no, el predeterminado de la instancia. */
    public int radiusFor(Integer own, UUID instance) { return own != null ? own : defaultRadius(instance); }

    /** Ubicación del Puesto como referencia (Bitácora, Relevo): [latitud, longitud, radio], o null si no tiene ubicación. */
    public double[] postReference(UUID instance, UUID postId) {
        PostOperationalConfig c = PostOperationalConfig.find("postId=?1 and instanceCountryId=?2", postId, instance).firstResult();
        return c == null || c.latitude == null || c.longitude == null ? null : new double[]{c.latitude, c.longitude, radiusFor(c.radiusM, instance)};
    }

    /** Valida un radio propio que llega de la web: null = usar el predeterminado. */
    public static Integer validRadius(Integer radius, String label) {
        if (radius == null) return null;
        if (radius < MIN_RADIUS || radius > MAX_RADIUS)
            throw new BadRequestException("El radio " + label + " debe estar entre " + MIN_RADIUS + " y " + MAX_RADIUS + " m");
        return radius;
    }

    /** Guarda el predeterminado de la instancia. */
    public void saveDefaultRadius(UUID instance, int radius, String username) {
        validRadius(radius, "predeterminado");
        em.createNativeQuery("""
            insert into operational_setting(instance_country_id,setting_key,setting_value,updated_by_username,updated_at) values(:t,:k,:v,:u,:at)
            on conflict (instance_country_id,setting_key) do update set setting_value=excluded.setting_value,updated_by_username=excluded.updated_by_username,updated_at=excluded.updated_at""")
            .setParameter("t", instance).setParameter("k", DEFAULT_RADIUS_KEY).setParameter("v", String.valueOf(radius))
            .setParameter("u", username).setParameter("at", Instant.now()).executeUpdate();
    }

    /** Quién y cuándo cambió el predeterminado; null si nunca se configuró (rige el valor del sistema). */
    @SuppressWarnings("unchecked")
    public Object[] audit(UUID instance) {
        List<Object[]> rows = em.createNativeQuery("select updated_by_username, updated_at from operational_setting where instance_country_id=:t and setting_key=:k")
            .setParameter("t", instance).setParameter("k", DEFAULT_RADIUS_KEY).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int fallbackRadius() { return fallbackRadius; }
}
