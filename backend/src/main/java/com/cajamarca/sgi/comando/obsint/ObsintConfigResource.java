package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.secelec.SecElecComponent;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Configuración OBSINT del Punto (diapositiva 10 de "OBSINT Estructura": "Vincular Consignas – Cámara – Horario").
 * Protocolo versionado igual que Consignas: BORRADOR (editable) → publicar → INACTIVO (inmutable) → activar → ACTIVO.
 * Un solo protocolo por Punto; "nueva versión" copia las consignas del último publicado a un BORRADOR.
 */
@Path("/api/obsint/config")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class ObsintConfigResource {
    static final Set<String> CATEGORIES = Set.of("SSO", "PRODUCTION", "LOGISTICS", "CLEANING", "MOBILITY", "CUSTOMER_SERVICE");
    static final int MAX_CAMERAS = 4;
    static final int MAX_CONSIGNAS = 50;

    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    public record SlotView(int dayOfWeek, String start, String end) {}
    public record ConsignaView(UUID id, String code, String title, String instruction, String category, int timeLimitSeconds, int position,
                               boolean active, List<UUID> cameraIds, List<SlotView> schedule) {
        static ConsignaView of(ObsintConsigna c) {
            return new ConsignaView(c.id, c.code, c.title, c.instruction, c.category, c.timeLimitSeconds, c.position, c.active,
                c.cameras.stream().map(cam -> cam.id).toList(),
                c.schedule.stream().sorted(Comparator.comparingInt(s -> s.dayOfWeek)).map(s -> new SlotView(s.dayOfWeek, s.startTime.toString(), s.endTime.toString())).toList());
        }
    }
    public record ProtocolView(UUID id, UUID pointId, String code, int versionNo, String status, Instant publishedAt, Instant activatedAt,
                               String updatedBy, Instant updatedAt, List<ConsignaView> consignas) {}
    public record CameraOption(UUID id, String code, String name, String location, String status) {}
    public record SaveConsigna(String title, String instruction, String category, Integer timeLimitSeconds, Boolean active,
                               List<UUID> cameraIds, List<SlotView> schedule) {}
    public record Summary(String status, String meta, Integer activeVersion, int activeConsignas, boolean hasDraft, List<String> warnings) {}

    /** Versiones del protocolo del Punto, la más reciente primero. */
    @GET @Path("/points/{pointId}/protocols") @Transactional
    public List<ProtocolView> protocols(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return versions(pointId).stream().map(this::view).toList();
    }

    /** Cámaras (componentes de Videovigilancia no inactivos) que se pueden asociar a las consignas del Punto. */
    @GET @Path("/points/{pointId}/cameras") @Transactional
    public List<CameraOption> cameras(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return pointCameras(pointId).stream().map(c -> new CameraOption(c.id, c.code, c.name, c.location, c.status.name())).toList();
    }

    /** Borrador para editar: el existente, una nueva versión del último publicado o la versión 1. */
    @POST @Path("/points/{pointId}/protocols/draft") @Transactional
    public ProtocolView draft(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        List<ObsintProtocol> all = versions(pointId);
        Optional<ObsintProtocol> existing = all.stream().filter(p -> "BORRADOR".equals(p.status)).findFirst();
        if (existing.isPresent()) return view(existing.get());
        ObsintProtocol p = new ObsintProtocol();
        p.id = UUID.randomUUID();
        p.instanceCountryId = platform.instanceCountryId();
        p.pointId = pointId;
        p.status = "BORRADOR";
        p.updatedBy = platform.username();
        if (all.isEmpty()) {
            p.seriesId = p.id;
            p.versionNo = 1;
            p.code = nextProtocolCode();
            p.persist();
        } else {
            ObsintProtocol source = all.get(0);
            p.seriesId = source.seriesId;
            p.basedOnProtocolId = source.id;
            p.versionNo = source.versionNo + 1;
            p.code = source.code;
            p.persist();
            for (ObsintConsigna old : consignas(source.id)) copy(old, p.id);
        }
        return view(p);
    }

    /** Descarta el borrador (las versiones publicadas no se tocan). */
    @DELETE @Path("/protocols/{id}") @Transactional
    public void discard(@PathParam("id") UUID id) {
        ObsintProtocol p = protocol(id);
        requireDraft(p);
        ObsintConsigna.delete("protocolId", p.id);
        p.delete();
    }

    /** Congela el borrador: queda INACTIVO e inmutable. Exige consignas completas. */
    @POST @Path("/protocols/{id}/publish") @Transactional
    public ProtocolView publish(@PathParam("id") UUID id) {
        ObsintProtocol p = protocol(id);
        requireDraft(p);
        List<ObsintConsigna> items = consignas(p.id);
        if (items.stream().noneMatch(c -> c.active)) throw new ObsintRuleException("El protocolo debe tener al menos una consigna activa.");
        for (ObsintConsigna c : items) {
            if (!c.active) continue;
            if (c.cameras.isEmpty()) throw new ObsintRuleException(c.code + ": asocie al menos una cámara.");
            if (c.cameras.stream().anyMatch(cam -> cam.status == SecElecComponent.Status.INACTIVE)) throw new ObsintRuleException(c.code + ": tiene cámaras inactivas.");
            if (c.schedule.isEmpty()) throw new ObsintRuleException(c.code + ": defina el horario de al menos un día.");
        }
        p.status = "INACTIVO";
        p.publishedAt = Instant.now();
        p.updatedBy = platform.username();
        return view(p);
    }

    /** Activa una versión publicada; la que estaba activa pasa a INACTIVO. La Consola usa la versión activa. */
    @POST @Path("/protocols/{id}/activate") @Transactional
    public ProtocolView activate(@PathParam("id") UUID id) {
        ObsintProtocol p = protocol(id);
        if (!"INACTIVO".equals(p.status)) throw new ObsintRuleException("Solo una versión publicada e inactiva puede activarse.");
        for (ObsintProtocol old : ObsintProtocol.<ObsintProtocol>list("instanceCountryId=?1 and pointId=?2 and status='ACTIVO'", p.instanceCountryId, p.pointId)) {
            old.status = "INACTIVO";
            old.updatedBy = platform.username();
        }
        em.flush(); // índice único: primero se libera la activa anterior
        p.status = "ACTIVO";
        p.activatedAt = Instant.now();
        p.updatedBy = platform.username();
        return view(p);
    }

    @POST @Path("/protocols/{id}/deactivate") @Transactional
    public ProtocolView deactivate(@PathParam("id") UUID id) {
        ObsintProtocol p = protocol(id);
        if (!"ACTIVO".equals(p.status)) throw new ObsintRuleException("Solo la versión activa puede inactivarse.");
        p.status = "INACTIVO";
        p.updatedBy = platform.username();
        return view(p);
    }

    @POST @Path("/protocols/{id}/consignas") @Transactional @Consumes(MediaType.APPLICATION_JSON)
    public ConsignaView createConsigna(@PathParam("id") UUID id, SaveConsigna body) {
        ObsintProtocol p = protocol(id);
        requireDraft(p);
        List<ObsintConsigna> items = consignas(p.id);
        if (items.size() >= MAX_CONSIGNAS) throw new ObsintRuleException("Máximo " + MAX_CONSIGNAS + " consignas por protocolo.");
        ObsintConsigna c = new ObsintConsigna();
        c.instanceCountryId = p.instanceCountryId;
        c.protocolId = p.id;
        c.pointId = p.pointId;
        c.code = "OBS-%03d".formatted(items.stream().mapToInt(ObsintConfigResource::codeNumber).max().orElse(0) + 1);
        c.position = items.stream().mapToInt(i -> i.position).max().orElse(0) + 1;
        apply(c, body);
        c.persist();
        return ConsignaView.of(c);
    }

    @PUT @Path("/consignas/{id}") @Transactional @Consumes(MediaType.APPLICATION_JSON)
    public ConsignaView updateConsigna(@PathParam("id") UUID id, SaveConsigna body) {
        ObsintConsigna c = consigna(id);
        requireDraft(protocol(c.protocolId));
        apply(c, body);
        return ConsignaView.of(c);
    }

    @DELETE @Path("/consignas/{id}") @Transactional
    public void deleteConsigna(@PathParam("id") UUID id) {
        ObsintConsigna c = consigna(id);
        requireDraft(protocol(c.protocolId));
        c.delete();
    }

    /** Estado para la fila "OBSINT" de Configuración del Punto. */
    @GET @Path("/points/{pointId}/summary") @Transactional
    public Summary summary(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        List<ObsintProtocol> all = versions(pointId);
        boolean hasDraft = all.stream().anyMatch(p -> "BORRADOR".equals(p.status));
        Optional<ObsintProtocol> active = all.stream().filter(p -> "ACTIVO".equals(p.status)).findFirst();
        if (active.isEmpty()) return new Summary("blocked", hasDraft ? "Borrador sin publicar · sin protocolo activo" : "Sin protocolo OBSINT activo", null, 0, hasDraft, List.of());
        List<ObsintConsigna> items = consignas(active.get().id).stream().filter(c -> c.active).toList();
        List<String> warnings = new ArrayList<>();
        for (ObsintConsigna c : items)
            c.cameras.stream().filter(cam -> cam.status != SecElecComponent.Status.ACTIVE)
                .forEach(cam -> warnings.add(c.code + ": la cámara " + cam.code + (cam.status == SecElecComponent.Status.INACTIVE ? " está inactiva" : " tiene observaciones")));
        return new Summary(warnings.isEmpty() ? "complete" : "warning",
            "Protocolo v%d activo · %d consignas".formatted(active.get().versionNo, items.size()), active.get().versionNo, items.size(), hasDraft, warnings);
    }

    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return Response.status(e.getResponse().getStatus()).entity(Map.of("message", e.getMessage())).type(MediaType.APPLICATION_JSON).build();
    }

    private void apply(ObsintConsigna c, SaveConsigna body) {
        if (body == null) throw new BadRequestException("Solicitud obligatoria.");
        c.title = text(body.title(), "El nombre de la consigna es obligatorio.", 160);
        c.instruction = text(body.instruction(), "La descripción de la consigna es obligatoria.", 1000);
        if (body.category() == null || !CATEGORIES.contains(body.category())) throw new BadRequestException("Categoría inválida.");
        c.category = body.category();
        if (body.timeLimitSeconds() == null || body.timeLimitSeconds() < 10 || body.timeLimitSeconds() > 3600) throw new BadRequestException("El tiempo máximo debe estar entre 10 segundos y 60 minutos.");
        c.timeLimitSeconds = body.timeLimitSeconds();
        c.active = body.active() == null || body.active();

        List<UUID> cameraIds = body.cameraIds() == null ? List.of() : body.cameraIds().stream().distinct().toList();
        if (cameraIds.size() > MAX_CAMERAS) throw new BadRequestException("Máximo " + MAX_CAMERAS + " cámaras por consigna.");
        Map<UUID, SecElecComponent> allowed = new HashMap<>();
        pointCameras(c.pointId).forEach(cam -> allowed.put(cam.id, cam));
        List<SecElecComponent> cameras = new ArrayList<>();
        for (UUID cameraId : cameraIds) {
            SecElecComponent cam = allowed.get(cameraId);
            if (cam == null) throw new BadRequestException("Solo se pueden asociar cámaras activas de este Punto.");
            cameras.add(cam);
        }
        c.cameras.clear();
        c.cameras.addAll(cameras);

        List<ObsintConsigna.Slot> slots = new ArrayList<>();
        Set<Integer> days = new HashSet<>();
        for (SlotView s : body.schedule() == null ? List.<SlotView>of() : body.schedule()) {
            if (s.dayOfWeek() < 1 || s.dayOfWeek() > 7 || !days.add(s.dayOfWeek())) throw new BadRequestException("Horario inválido: un tramo por día, de lunes (1) a domingo (7).");
            LocalTime start = time(s.start()), end = time(s.end());
            if (!end.isAfter(start)) throw new BadRequestException("Horario inválido: la hora de fin debe ser mayor que la de inicio.");
            slots.add(new ObsintConsigna.Slot(s.dayOfWeek(), start, end));
        }
        c.schedule.clear();
        c.schedule.addAll(slots);
    }

    private void copy(ObsintConsigna old, UUID protocolId) {
        ObsintConsigna c = new ObsintConsigna();
        c.instanceCountryId = old.instanceCountryId;
        c.protocolId = protocolId;
        c.pointId = old.pointId;
        c.code = old.code;
        c.title = old.title;
        c.instruction = old.instruction;
        c.category = old.category;
        c.timeLimitSeconds = old.timeLimitSeconds;
        c.position = old.position;
        c.active = old.active;
        c.cameras.addAll(old.cameras);
        old.schedule.forEach(s -> c.schedule.add(new ObsintConsigna.Slot(s.dayOfWeek, s.startTime, s.endTime)));
        c.persist();
    }

    private ProtocolView view(ObsintProtocol p) {
        return new ProtocolView(p.id, p.pointId, p.code, p.versionNo, p.status, p.publishedAt, p.activatedAt, p.updatedBy, p.updatedAt,
            consignas(p.id).stream().map(ConsignaView::of).toList());
    }

    private List<ObsintProtocol> versions(UUID pointId) {
        return ObsintProtocol.list("instanceCountryId=?1 and pointId=?2 order by versionNo desc", platform.instanceCountryId(), pointId);
    }

    private List<ObsintConsigna> consignas(UUID protocolId) {
        return ObsintConsigna.list("protocolId=?1 order by position, code", protocolId);
    }

    private List<SecElecComponent> pointCameras(UUID pointId) {
        return SecElecComponent.list("instanceCountryId=?1 and pointId=?2 and family=?3 and status<>?4 order by code",
            platform.instanceCountryId(), pointId, SecElecComponent.Family.VIDEO, SecElecComponent.Status.INACTIVE);
    }

    private ObsintProtocol protocol(UUID id) {
        ObsintProtocol p = ObsintProtocol.<ObsintProtocol>find("id=?1 and instanceCountryId=?2", id, platform.instanceCountryId()).firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Protocolo no encontrado"));
        platform.requirePoint(p.pointId);
        return p;
    }

    private ObsintConsigna consigna(UUID id) {
        return ObsintConsigna.<ObsintConsigna>find("id=?1 and instanceCountryId=?2", id, platform.instanceCountryId()).firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Consigna no encontrada"));
    }

    private static void requireDraft(ObsintProtocol p) {
        if (!"BORRADOR".equals(p.status)) throw new ObsintRuleException("La versión publicada no se puede modificar. Cree una nueva versión.");
    }

    private String nextProtocolCode() {
        long n = ObsintProtocol.<ObsintProtocol>list("instanceCountryId", platform.instanceCountryId()).stream().map(p -> p.seriesId).distinct().count() + 1;
        String code;
        do { code = "PRO-OBS-%04d".formatted(n++); } while (ObsintProtocol.count("instanceCountryId=?1 and code=?2", platform.instanceCountryId(), code) > 0);
        return code;
    }

    private static int codeNumber(ObsintConsigna c) {
        try { return Integer.parseInt(c.code.substring(c.code.lastIndexOf('-') + 1)); } catch (RuntimeException e) { return 0; }
    }

    private static LocalTime time(String value) {
        try { return LocalTime.parse(value); } catch (DateTimeParseException | NullPointerException e) { throw new BadRequestException("Hora inválida: use el formato HH:mm."); }
    }

    private static String text(String value, String message, int max) {
        if (value == null || value.isBlank()) throw new BadRequestException(message);
        String v = value.trim();
        if (v.length() > max) throw new BadRequestException("Texto demasiado largo (máximo " + max + " caracteres).");
        return v;
    }
}
