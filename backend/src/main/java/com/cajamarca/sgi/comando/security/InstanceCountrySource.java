package com.cajamarca.sgi.comando.security;

import com.cajamarca.sgi.comando.interconnections.CoreInterconnectionResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Instancia PE de SGI: Comando sin ID ni nemotecnico configurado (solo IDENT tiene dominio del correo -> PE, DEC-46).
 * <ul>
 *   <li>Con persona: el PE viene en su token de IDENT (claim {@code active_instance_country_id}).</li>
 *   <li>Sin persona (arranque, publicar Roles, usuario de operador): las Instancias PE donde CORE tiene ACTIVA la
 *       interconexion de login de SGI_COM ({@code SGI_COM_IDENT_0001}). Cada consulta a CORE sigue siendo por PE + ambiente.</li>
 * </ul>
 */
@ApplicationScoped
public class InstanceCountrySource {
    private static final Logger LOG = Logger.getLogger(InstanceCountrySource.class);
    private static final Duration TTL = Duration.ofMinutes(5);

    @Inject CoreInterconnectionResolver core;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "sgi.core.catalog-base-url") Optional<String> catalogBaseUrl;
    @ConfigProperty(name = "sgi.ident.login-interconnection", defaultValue = InterconnectionIds.IDENT_AUTH) String loginInterconnection;
    @ConfigProperty(name = "sgi.interconnections.core-timeout-ms", defaultValue = "5000") int timeoutMs;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile List<UUID> known = List.of();
    private volatile Instant knownUntil = Instant.EPOCH;

    /** PE de la peticion actual: el del token si la persona entro con IDENT; si no, el de CORE. */
    public UUID current() {
        UUID fromToken = fromToken();
        return fromToken != null ? fromToken : any();
    }

    /** PE del token de IDENT de la peticion actual, o null (sin peticion, sin token o sin PE). */
    public UUID fromToken() {
        if (!Arc.container().requestContext().isActive()) return null;
        SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
        return IdentIdentity.instanceCountryId(identity);
    }

    /** true si el PE es una Instancia PE donde esta SGI: Comando segun CORE. */
    public boolean belongs(UUID instanceCountryId) {
        return instanceCountryId != null && instances().contains(instanceCountryId);
    }

    /** Primera Instancia PE de SGI: Comando segun CORE (para tareas sin persona). */
    public UUID any() {
        List<UUID> list = instances();
        if (list.isEmpty()) throw new IllegalStateException("CORE: SGI_COM no tiene " + loginInterconnection + " ACTIVA en ninguna Instancia PE");
        return list.getFirst();
    }

    /** Instancias PE donde CORE tiene ACTIVA la interconexion de login de SGI_COM (cache 5 min; si CORE falla, la ultima). */
    public synchronized List<UUID> instances() {
        if (Instant.now().isBefore(knownUntil) && !known.isEmpty()) return known;
        try {
            List<UUID> mine = new ArrayList<>();
            for (JsonNode ic : catalog()) {
                UUID id;
                try { id = UUID.fromString(ic.path("id").asText("")); } catch (IllegalArgumentException e) { continue; }
                try { core.resolve(loginInterconnection, null, id); mine.add(id); }
                catch (RuntimeException notActiveHere) { /* SGI_COM no esta en esta Instancia PE */ }
            }
            if (!mine.isEmpty()) { known = List.copyOf(mine); knownUntil = Instant.now().plus(TTL); }
        } catch (RuntimeException e) {
            LOG.warnf("No se pudo consultar a CORE las Instancias PE de SGI_COM (%s); se usa la ultima lista conocida", e.getMessage());
        }
        return known;
    }

    private JsonNode catalog() {
        String base = catalogBaseUrl.orElse("").trim().replaceAll("/+$", "");
        if (base.isEmpty()) throw new IllegalStateException("No esta configurada la URL del catalogo CORE (sgi.core.catalog-base-url)");
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/catalog/instance-countries"))
                    .timeout(Duration.ofMillis(Math.max(timeoutMs, 250))).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new IllegalStateException("CORE respondio HTTP " + r.statusCode() + " a la lista de Instancias PE");
            JsonNode n = mapper.readTree(r.body());
            JsonNode rows = n.isArray() ? n : n.path("items");
            if (!rows.isArray()) throw new IllegalStateException("CORE devolvio la lista de Instancias PE con un formato inesperado");
            return rows;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("CORE no responde: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta a CORE interrumpida", e);
        }
    }
}
