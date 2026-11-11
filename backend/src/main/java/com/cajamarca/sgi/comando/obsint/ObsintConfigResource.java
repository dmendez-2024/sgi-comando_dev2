package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.consignments.Consignment;
import com.cajamarca.sgi.comando.consignments.ConsignmentPostScope;
import com.cajamarca.sgi.comando.secelec.SecElecComponent;
import com.cajamarca.sgi.comando.secelec.ElectronicMonitoringDevice;
import com.cajamarca.sgi.comando.secelec.ElectronicMonitoringShift;
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
 * La pantalla usa "guardar = activar" (/points/{pointId}/tasks): cada cambio de una tarea crea la versión siguiente ya ACTIVA,
 * así las versiones quedan como historial sin pasos de Publicar/Activar.
 */
@Path("/api/obsint/config")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class ObsintConfigResource {
    static final Set<String> CATEGORIES = Set.of("SSO", "PRODUCTION", "LOGISTICS", "CLEANING", "MOBILITY", "CUSTOMER_SERVICE");
    static final Set<String> FREQUENCIES = Set.of("EVERY_SHIFT", "EVERY_2_HOURS", "EVERY_4_HOURS", "DAILY");
    static final int MAX_CAMERAS = 4;
    static final int MAX_CONSIGNAS = 50;

    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    public record SlotView(int dayOfWeek, String start, String end) {}
    public record ConsignaView(UUID id, String code, String consignmentCode, String title, String description, String instruction, String category, String frequency, int timeLimitSeconds, int position,
                               boolean active, List<UUID> cameraIds, List<SlotView> schedule) {
        static ConsignaView of(ObsintConsigna c) {
            return new ConsignaView(c.id, c.code, c.consignmentCode, c.title, c.description, c.instruction, c.category, c.frequency, c.timeLimitSeconds, c.position, c.active,
                c.cameras.stream().map(cam -> cam.id).toList(),
                c.schedule.stream().sorted(Comparator.comparingInt(s -> s.dayOfWeek)).map(s -> new SlotView(s.dayOfWeek, s.startTime.toString(), s.endTime.toString())).toList());
        }
    }
    public record ProtocolView(UUID id, UUID pointId, String code, int versionNo, String status, Instant publishedAt, Instant activatedAt,
                               String updatedBy, Instant updatedAt, List<ConsignaView> consignas) {}
    /** deviceId: dispositivo OBSINT de SIC:COM al que pertenece la cámara (null si no tiene). */
    public record CameraOption(UUID id, String code, String name, String location, String status, UUID deviceId) {}
    /** Consigna de Consignas marcada OBSINT que se puede vincular a una tarea. */
    public record ConsignmentOption(String code, String title, String instruction) {}
    public record SaveConsigna(String consignmentCode, String title, String description, String instruction, String category, String frequency, Integer timeLimitSeconds, Boolean active,
                               List<UUID> cameraIds, List<SlotView> schedule) {}
    /** enabled=false: el Punto no tiene orden de servicio OBSINT (SEN-01); la fila se muestra deshabilitada. */
    public record Summary(boolean enabled, String status, String meta, Integer activeVersion, int activeConsignas, boolean hasDraft, List<String> warnings) {}
    /**
     * Horario comercial sugerido para una tarea: el de los dispositivos a los que apunta su consigna o, si no apunta a ninguno,
     * el de los dispositivos OBSINT activos del Punto. Solo si todos tienen el mismo horario.
     * deviceIds: dispositivos de la consigna (vacío si no apunta a ninguno); la pantalla ofrece solo las cámaras de esos dispositivos.
     */
    public record ScheduleSuggestion(boolean available, String message, List<SlotView> slots, List<UUID> deviceIds) {}
    /** Resultado de guardar una tarea: la nueva versión activa y el código de la tarea guardada. */
    public record TaskSaved(ProtocolView protocol, String code) {}

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
        return pointCameras(pointId).stream().map(c -> new CameraOption(c.id, c.code, c.name, c.location, c.status.name(), c.electronicDeviceId)).toList();
    }

    /** Consignas del protocolo de Consignas activo del Punto marcadas con el "puesto" OBSINT: las que se pueden vincular. */
    @GET @Path("/points/{pointId}/consignments") @Transactional
    public List<ConsignmentOption> consignments(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return platform.obsintConsignments(pointId).values().stream().map(c -> new ConsignmentOption(c.code(), c.title(), c.instruction())).toList();
    }

    @GET @Path("/points/{pointId}/schedule-suggestion") @Transactional
    public ScheduleSuggestion scheduleSuggestion(@PathParam("pointId") UUID pointId, @QueryParam("consignmentCode") String consignmentCode) {
        platform.requirePoint(pointId);
        if (consignmentCode == null || consignmentCode.isBlank()) return new ScheduleSuggestion(false, "Seleccione una consigna para consultar su horario comercial.", List.of(), List.of());
        if (!platform.obsintConsignments(pointId).containsKey(consignmentCode.trim()))
            return new ScheduleSuggestion(false, "La consigna no está vigente con alcance OBSINT.", List.of(), List.of());
        List<UUID> deviceIds = consignmentDevices(pointId, consignmentCode.trim());
        List<ElectronicMonitoringDevice> devices;
        if (deviceIds.isEmpty()) {
            // Consigna con la casilla OBSINT y alcance "Todo el Punto": el horario sale de los dispositivos OBSINT activos del Punto.
            devices = ElectronicMonitoringDevice.list("instanceCountryId=?1 and pointId=?2 and upper(postType)='OBSINT' and upper(commercialStatus)='ACTIVE' order by code",
                platform.instanceCountryId(), pointId);
            if (devices.isEmpty())
                return new ScheduleSuggestion(false, "El Punto no tiene dispositivos OBSINT activos de SIC:COM; defina el horario manualmente.", List.of(), deviceIds);
        } else {
            // Varios dispositivos (por ejemplo, 3 cámaras del mismo producto) sirven si todos tienen el mismo horario contratado.
            devices = ElectronicMonitoringDevice.list("id in ?1 and instanceCountryId=?2 and pointId=?3", deviceIds, platform.instanceCountryId(), pointId);
            if (devices.size() != deviceIds.size() || devices.stream().anyMatch(d -> !"ACTIVE".equalsIgnoreCase(d.commercialStatus)))
                return new ScheduleSuggestion(false, "El dispositivo asociado no está activo.", List.of(), deviceIds);
        }
        ElectronicMonitoringShift shift = null;
        for (ElectronicMonitoringDevice device : devices) {
            List<ElectronicMonitoringShift> shifts = ElectronicMonitoringShift.list(
                "instanceCountryId=?1 and deviceId=?2 and active=true order by shiftCode", platform.instanceCountryId(), device.id);
            if (shifts.size() != 1) return new ScheduleSuggestion(false, "El dispositivo " + device.code + " tiene varios horarios activos; seleccione el horario operativo manualmente.", List.of(), deviceIds);
            ElectronicMonitoringShift own = shifts.getFirst();
            if (shift != null && !(own.startTime.equals(shift.startTime) && own.endTime.equals(shift.endTime) && java.util.Objects.equals(own.dayMask, shift.dayMask)))
                return new ScheduleSuggestion(false, deviceIds.isEmpty()
                    ? "Los dispositivos OBSINT del Punto tienen horarios distintos; marque en la consigna su dispositivo o defina el horario manualmente."
                    : "Los dispositivos de la consigna tienen horarios distintos; cree una consigna por dispositivo o defina el horario manualmente.", List.of(), deviceIds);
            shift = own;
        }
        if (shift.dayMask == null || shift.dayMask < 1 || shift.dayMask > 127) return new ScheduleSuggestion(false, "El horario comercial tiene días inválidos.", List.of(), deviceIds);
        if (!shift.endTime.isAfter(shift.startTime)) return new ScheduleSuggestion(false, "El horario comercial cruza medianoche; regístrelo manualmente en dos días.", List.of(), deviceIds);
        List<SlotView> slots = new ArrayList<>();
        for (int day = 1; day <= 7; day++) if ((shift.dayMask & (1 << (day - 1))) != 0)
            slots.add(new SlotView(day, shift.startTime.toString(), shift.endTime.toString()));
        String who = devices.size() == 1 ? devices.getFirst().code : devices.size() + " dispositivos";
        return new ScheduleSuggestion(true, "Horario comercial de " + who + " · " + shift.shiftName, slots, deviceIds);
    }

    /** Horario contratado (turno activo de SIC:COM) de un dispositivo OBSINT. dayMask: bit 0 = lunes … bit 6 = domingo. */
    public record DeviceShiftView(String name, String start, String end, Integer dayMask) {}
    /** Dispositivo OBSINT del Punto con sus horarios activos; Consignas → Alcance lo usa para guiar una consigna por dispositivo. */
    /** cameras: cámaras de Videovigilancia no inactivas asignadas al dispositivo en Componentes de Seguridad. */
    public record DeviceScheduleView(UUID deviceId, String code, String name, boolean active, int cameras, List<DeviceShiftView> shifts) {}

    /**
     * Dispositivos OBSINT del Punto y su horario contratado. Solo exige acceso al Punto: sin dispositivos OBSINT devuelve la lista vacía.
     * Misma regla que la sugerencia de horario: varias tareas toman su horario solas solo si cada consigna cubre dispositivos con el mismo horario.
     */
    @GET @Path("/points/{pointId}/device-schedules") @Transactional
    public List<DeviceScheduleView> deviceSchedules(@PathParam("pointId") UUID pointId) {
        platform.requirePointAccess(pointId);
        List<ElectronicMonitoringDevice> devices = ElectronicMonitoringDevice.list("instanceCountryId=?1 and pointId=?2 and upper(postType)='OBSINT' order by code",
            platform.instanceCountryId(), pointId);
        Map<UUID, Long> cameras = new HashMap<>();
        for (SecElecComponent camera : pointCameras(pointId))
            if (camera.electronicDeviceId != null) cameras.merge(camera.electronicDeviceId, 1L, Long::sum);
        List<DeviceScheduleView> out = new ArrayList<>();
        for (ElectronicMonitoringDevice device : devices) {
            List<DeviceShiftView> shifts = ElectronicMonitoringShift.<ElectronicMonitoringShift>list(
                    "instanceCountryId=?1 and deviceId=?2 and active=true order by shiftCode", platform.instanceCountryId(), device.id).stream()
                .map(s -> new DeviceShiftView(s.shiftName, s.startTime == null ? null : s.startTime.toString(), s.endTime == null ? null : s.endTime.toString(), s.dayMask))
                .toList();
            out.add(new DeviceScheduleView(device.id, device.code, device.name, "ACTIVE".equalsIgnoreCase(device.commercialStatus),
                cameras.getOrDefault(device.id, 0L).intValue(), shifts));
        }
        return out;
    }

    /** Dispositivos OBSINT de SIC:COM a los que apunta la consigna vigente de Consignas con ese código. */
    private List<UUID> consignmentDevices(UUID pointId, String consignmentCode) {
        Consignment consignment = Consignment.<Consignment>find("instanceCountryId=?1 and pointId=?2 and code=?3 and status='VIGENTE'",
            platform.instanceCountryId(), pointId, consignmentCode).firstResultOptional().orElse(null);
        if (consignment == null) return List.of();
        return ConsignmentPostScope.<ConsignmentPostScope>list("instanceCountryId=?1 and consignmentId=?2 and electronicDeviceId is not null",
            platform.instanceCountryId(), consignment.id).stream().map(scope -> scope.electronicDeviceId).distinct().toList();
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
            ObsintProtocol source = all.stream().filter(item -> "ACTIVO".equals(item.status)).findFirst().orElse(all.get(0));
            p.seriesId = source.seriesId;
            p.basedOnProtocolId = source.id;
            p.versionNo = all.stream().filter(item -> source.seriesId.equals(item.seriesId))
                .mapToInt(item -> item.versionNo).max().orElse(source.versionNo) + 1;
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
        validate(items);
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
        em.flush();
        platform.syncDeviceConfiguration(p.pointId);
        return view(p);
    }

    @POST @Path("/protocols/{id}/deactivate") @Transactional
    public ProtocolView deactivate(@PathParam("id") UUID id) {
        ObsintProtocol p = protocol(id);
        if (!"ACTIVO".equals(p.status)) throw new ObsintRuleException("Solo la versión activa puede inactivarse.");
        p.status = "INACTIVO";
        p.updatedBy = platform.username();
        em.flush();
        platform.syncDeviceConfiguration(p.pointId);
        return view(p);
    }

    /** Agrega una tarea y deja activa la nueva versión. Si el Punto no tiene protocolo, crea la versión 1. */
    @POST @Path("/points/{pointId}/tasks") @Transactional @Consumes(MediaType.APPLICATION_JSON)
    public TaskSaved addTask(@PathParam("pointId") UUID pointId, SaveConsigna body) {
        ObsintProtocol p = nextVersion(pointId);
        ConsignaView c = createConsigna(p.id, body);
        return new TaskSaved(activateNow(p), c.code());
    }

    /** Cambia una tarea (por su código, que se conserva entre versiones) y deja activa la nueva versión. */
    @PUT @Path("/points/{pointId}/tasks/{code}") @Transactional @Consumes(MediaType.APPLICATION_JSON)
    public TaskSaved updateTask(@PathParam("pointId") UUID pointId, @PathParam("code") String code, SaveConsigna body) {
        ObsintProtocol p = nextVersion(pointId);
        ObsintConsigna c = task(p, code);
        apply(c, body);
        return new TaskSaved(activateNow(p), c.code);
    }

    /** Quita una tarea y deja activa la nueva versión (puede quedar sin tareas: la Consola no muestra nada del Punto). */
    @DELETE @Path("/points/{pointId}/tasks/{code}") @Transactional
    public ProtocolView deleteTask(@PathParam("pointId") UUID pointId, @PathParam("code") String code) {
        ObsintProtocol p = nextVersion(pointId);
        task(p, code).delete();
        return activateNow(p);
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
        platform.requirePointAccess(pointId);
        if (!platform.obsintEnabled(pointId))
            return new Summary(false, "blocked", "Requiere orden de servicio OBSINT", null, 0, false, List.of());
        List<ObsintProtocol> all = versions(pointId);
        boolean hasDraft = all.stream().anyMatch(p -> "BORRADOR".equals(p.status));
        Optional<ObsintProtocol> active = all.stream().filter(p -> "ACTIVO".equals(p.status)).findFirst();
        if (active.isEmpty()) return new Summary(true, "blocked", hasDraft ? "Borrador sin publicar · sin protocolo activo" : "Sin protocolo OBSINT activo", null, 0, hasDraft, List.of());
        List<ObsintConsigna> items = consignas(active.get().id).stream().filter(c -> c.active).toList();
        if (items.isEmpty()) return new Summary(true, "blocked", "Protocolo v%d activo sin tareas activas".formatted(active.get().versionNo), active.get().versionNo, 0, hasDraft, List.of());
        List<String> warnings = new ArrayList<>();
        Map<String, ObsintPlatform.LinkedConsignment> linkable = platform.obsintConsignments(pointId);
        for (ObsintConsigna c : items)
            if (c.consignmentCode != null && !linkable.containsKey(c.consignmentCode))
                warnings.add(c.code + ": la consigna " + c.consignmentCode + " ya no está vigente con OBSINT en Consignas; la Consola no la mostrará");
        for (ObsintConsigna c : items)
            c.cameras.stream().filter(cam -> cam.status != SecElecComponent.Status.ACTIVE)
                .forEach(cam -> warnings.add(c.code + ": la cámara " + cam.code + (cam.status == SecElecComponent.Status.INACTIVE ? " está inactiva" : " tiene observaciones")));
        warnings.addAll(deviceWarnings(pointId, items));
        return new Summary(true, warnings.isEmpty() ? "complete" : "warning",
            "Protocolo v%d activo · %d consignas".formatted(active.get().versionNo, items.size()), active.get().versionNo, items.size(), hasDraft, warnings);
    }

    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return ObsintPlatform.errorResponse(e);
    }

    /**
     * Avisos de la orden de SIC:COM: dispositivos OBSINT activos sin cámara asignada o sin tarea en el protocolo activo,
     * y tareas con cámaras de otro dispositivo.
     */
    private List<String> deviceWarnings(UUID pointId, List<ObsintConsigna> items) {
        List<String> warnings = new ArrayList<>();
        List<ElectronicMonitoringDevice> devices = ElectronicMonitoringDevice.list(
            "instanceCountryId=?1 and pointId=?2 and upper(postType)='OBSINT' and upper(commercialStatus)='ACTIVE' order by code", platform.instanceCountryId(), pointId);
        if (devices.isEmpty()) return warnings;
        List<SecElecComponent> cameras = pointCameras(pointId);
        Set<UUID> covered = new HashSet<>();
        for (ObsintConsigna c : items) {
            if (c.consignmentCode == null) continue;
            List<UUID> own = consignmentDevices(pointId, c.consignmentCode);
            covered.addAll(own);
            c.cameras.stream().filter(cam -> !own.isEmpty() && cam.electronicDeviceId != null && !own.contains(cam.electronicDeviceId))
                .forEach(cam -> warnings.add(c.code + ": la cámara " + cam.code + " es de otro dispositivo de SIC:COM"));
        }
        for (ElectronicMonitoringDevice d : devices) {
            if (cameras.stream().noneMatch(cam -> d.id.equals(cam.electronicDeviceId)))
                warnings.add("El dispositivo " + d.code + " (" + d.name + ") no tiene cámaras asignadas en Componentes de Seguridad");
            else if (!covered.contains(d.id))
                warnings.add("El dispositivo " + d.code + " (" + d.name + ") no tiene tarea OBSINT");
        }
        return warnings;
    }

    private void apply(ObsintConsigna c, SaveConsigna body) {
        if (body == null) throw new BadRequestException("Solicitud obligatoria.");
        if (body.consignmentCode() != null && !body.consignmentCode().isBlank()) {
            // Vinculada: nombre e instrucción salen de Consignas; aquí solo se agregan cámaras, horario, tiempo y categoría.
            String code = body.consignmentCode().trim();
            ObsintPlatform.LinkedConsignment source = platform.obsintConsignments(c.pointId).get(code);
            if (source == null) throw new BadRequestException("La consigna " + code + " no está vigente con OBSINT en el protocolo de Consignas activo del Punto.");
            if (ObsintConsigna.count("protocolId=?1 and consignmentCode=?2 and id<>?3", c.protocolId, code, c.id == null ? new UUID(0, 0) : c.id) > 0)
                throw new BadRequestException("La consigna " + code + " ya está vinculada a otra tarea de este protocolo.");
            c.consignmentCode = code;
            c.title = source.title().length() > 160 ? source.title().substring(0, 160) : source.title();
            c.instruction = source.instruction().length() > 1000 ? source.instruction().substring(0, 1000) : source.instruction();
            c.description = body.description() == null || body.description().isBlank() ? c.instruction : text(body.description(), "", 1000);
        } else {
            c.consignmentCode = null;
            c.title = text(body.title(), "El nombre de la consigna es obligatorio.", 160);
            c.instruction = text(body.instruction(), "La descripción de la consigna es obligatoria.", 1000);
            // Sin descripción se usa la instrucción, igual que V101 con las consignas anteriores.
            c.description = body.description() == null || body.description().isBlank() ? c.instruction : text(body.description(), "", 1000);
        }
        if (body.category() == null || !CATEGORIES.contains(body.category())) throw new BadRequestException("Categoría inválida.");
        c.category = body.category();
        String frequency = body.frequency() == null ? "EVERY_SHIFT" : body.frequency();
        if (!FREQUENCIES.contains(frequency)) throw new BadRequestException("Frecuencia inválida.");
        c.frequency = frequency;
        if (body.timeLimitSeconds() == null || body.timeLimitSeconds() < 10 || body.timeLimitSeconds() > 3600) throw new BadRequestException("El tiempo máximo debe estar entre 10 segundos y 60 minutos.");
        c.timeLimitSeconds = body.timeLimitSeconds();
        c.active = body.active() == null || body.active();

        List<UUID> cameraIds = body.cameraIds() == null ? List.of() : body.cameraIds().stream().distinct().toList();
        if (cameraIds.size() > MAX_CAMERAS) throw new BadRequestException("Máximo " + MAX_CAMERAS + " cámaras por consigna.");
        Map<UUID, SecElecComponent> allowed = new HashMap<>();
        pointCameras(c.pointId).forEach(cam -> allowed.put(cam.id, cam));
        // Consigna de un dispositivo de SIC:COM: solo sus cámaras (las cámaras sin dispositivo se aceptan mientras se asignan).
        List<UUID> devices = c.consignmentCode == null ? List.of() : consignmentDevices(c.pointId, c.consignmentCode);
        List<SecElecComponent> cameras = new ArrayList<>();
        for (UUID cameraId : cameraIds) {
            SecElecComponent cam = allowed.get(cameraId);
            if (cam == null) throw new BadRequestException("Solo se pueden asociar cámaras activas de este Punto.");
            if (!devices.isEmpty() && cam.electronicDeviceId != null && !devices.contains(cam.electronicDeviceId))
                throw new BadRequestException("La cámara " + cam.code + " pertenece a otro dispositivo de SIC:COM, no al de la consigna " + c.consignmentCode + ".");
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

    /** Reglas para que una versión pueda usarse en la Consola. */
    private static void validate(List<ObsintConsigna> items) {
        // Una tarea cuya consigna dejó de estar marcada OBSINT no bloquea: la Consola la salta y el resumen lo advierte.
        for (ObsintConsigna c : items) {
            if (!c.active) continue;
            if (c.cameras.isEmpty()) throw new ObsintRuleException(c.code + ": asocie al menos una cámara.");
            if (c.cameras.stream().anyMatch(cam -> cam.status == SecElecComponent.Status.INACTIVE)) throw new ObsintRuleException(c.code + ": tiene cámaras inactivas.");
            if (c.schedule.isEmpty()) throw new ObsintRuleException(c.code + ": defina el horario de al menos un día.");
        }
    }

    /** Versión siguiente (copia de la activa, o de la última) donde se aplica un cambio de "guardar = activar". */
    private ObsintProtocol nextVersion(UUID pointId) {
        platform.requirePoint(pointId);
        // Un borrador del flujo Publicar → Activar ya no se puede abrir en la pantalla: se descarta y se parte de la activa.
        for (ObsintProtocol old : versions(pointId)) {
            if (!"BORRADOR".equals(old.status)) continue;
            ObsintConsigna.delete("protocolId", old.id);
            old.delete();
        }
        em.flush();
        return protocol(draft(pointId).id());
    }

    /** Publica y activa en el mismo paso; si algo no cumple las reglas, la transacción se revierte y la versión activa sigue igual. */
    private ProtocolView activateNow(ObsintProtocol p) {
        em.flush();
        validate(consignas(p.id));
        p.status = "INACTIVO";
        p.publishedAt = Instant.now();
        return activate(p.id);
    }

    private ObsintConsigna task(ObsintProtocol p, String code) {
        return ObsintConsigna.<ObsintConsigna>find("protocolId=?1 and code=?2", p.id, code).firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Tarea " + code + " no encontrada en la versión activa"));
    }

    private void copy(ObsintConsigna old, UUID protocolId) {
        ObsintConsigna c = new ObsintConsigna();
        c.instanceCountryId = old.instanceCountryId;
        c.protocolId = protocolId;
        c.pointId = old.pointId;
        c.code = old.code;
        c.consignmentCode = old.consignmentCode;
        c.title = old.title;
        c.description = old.description;
        c.instruction = old.instruction;
        c.category = old.category;
        c.frequency = old.frequency;
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
