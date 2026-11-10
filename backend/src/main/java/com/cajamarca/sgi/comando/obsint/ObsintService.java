package com.cajamarca.sgi.comando.obsint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Casos de uso de la Consola OBSINT. Cada monitorista solo ve y modifica sus propios casos. */
@ApplicationScoped
public class ObsintService {
    private static final Logger LOG = Logger.getLogger(ObsintService.class);
    static final int MAX_EVIDENCE_PER_TASK = 6;
    static final long MAX_EVIDENCE_BYTES = 5L * 1024 * 1024;

    @Inject ObsintPlatform platform;
    @Inject EntityManager em;
    @Inject com.cajamarca.sgi.comando.socket.SocketConfig socket;
    @Inject ObsintAgentRecipients recipients;
    @Inject jakarta.enterprise.event.Event<ObsintMessageQueued> queued;
    @Inject com.cajamarca.sgi.comando.territory.MonitorShiftScope monitorScope;
    @Inject com.cajamarca.sgi.comando.communication.CommunicationRooms rooms;
    /** Si es false, cada consigna entra en todos los casos dentro de su horario (útil para demos locales). */
    @ConfigProperty(name = "sgi.obsint.apply-frequency", defaultValue = "true") boolean applyFrequency;

    /** Postgres guarda microsegundos: truncar evita que el deadline cambie al releerlo. */
    public Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }

    /** El caso en curso del monitorista, si lo hay. */
    @Transactional
    public Optional<ObsintCase> current() {
        return ObsintCase.<ObsintCase>find("instanceCountryId=?1 and operator=?2 and status=?3 order by startedAt desc",
            platform.instanceCountryId(), platform.username(), ObsintCase.Status.IN_PROGRESS).firstResultOptional();
    }

    /**
     * Abre un caso con las consignas activas de los protocolos OBSINT ACTIVOS cuyo horario cubre este momento,
     * solo de los Puntos de las Compañías del alcance del monitorista. Las tareas vinculadas a Consignas entran solo si esa
     * consigna sigue vigente y marcada OBSINT, y llevan su instrucción actual.
     * Si ya hay uno en curso se devuelve ese: un monitorista trabaja un caso a la vez.
     */
    @Transactional
    public ObsintCase open() {
        Optional<ObsintCase> running = current();
        if (running.isPresent()) return running.get();
        UUID tenantId = platform.instanceCountryId();
        ZonedDateTime local = now().atZone(ObsintPlatform.ZONE);
        List<ObsintConsigna> scheduled = ObsintConsigna.<ObsintConsigna>list(
                "instanceCountryId=?1 and active=true and protocolId in (select p.id from ObsintProtocol p where p.status='ACTIVO' and p.instanceCountryId=?1) order by pointId, position, code", tenantId)
            .stream().filter(c -> c.scheduledAt(local.getDayOfWeek().getValue(), local.toLocalTime())).toList();
        Set<UUID> visible = platform.visiblePoints(scheduled.stream().map(c -> c.pointId).collect(Collectors.toSet()));
        Map<UUID, Map<String, ObsintPlatform.LinkedConsignment>> linked = platform.obsintConsignments(visible);
        Instant at = now();
        Map<String, Instant> lastAnswers = applyFrequency ? lastAnswers(tenantId, visible) : Map.of();
        List<ObsintConsigna> consignas = scheduled.stream().filter(c -> visible.contains(c.pointId))
            .filter(c -> c.consignmentCode == null || Optional.ofNullable(linked.getOrDefault(c.pointId, Map.of()).get(c.consignmentCode))
                .filter(source -> source.validAt(at)).isPresent())
            .filter(c -> due(c.frequency, lastAnswers.get(c.pointId + "|" + c.code), at))
            .toList();
        if (consignas.isEmpty()) throw new ObsintRuleException("No hay consignas OBSINT pendientes en este horario en las Compañías de su alcance. Las ya revisadas vuelven según su frecuencia.");
        long number = ((Number) em.createNativeQuery("select nextval('obsint_case_code_seq')").getSingleResult()).longValue();
        ObsintCase c = ObsintCase.open(tenantId, "CAJ-OBS-%03d".formatted(number), platform.username(), consignas, now());
        for (ObsintCaseTask t : c.tasks)
            if (t.consigna.consignmentCode != null) t.instruction = trim(linked.get(t.consigna.pointId).get(t.consigna.consignmentCode).instruction(), 1000);
        c.persist();
        LOG.infof("obsint_case_opened case=%s actor=%s tasks=%d", c.code, c.operator, c.tasks.size());
        return c;
    }

    @Transactional
    public ObsintCase get(UUID caseId) {
        return ObsintCase.<ObsintCase>find("id=?1 and instanceCountryId=?2 and operator=?3", caseId, platform.instanceCountryId(), platform.username())
            .firstResultOptional().orElseThrow(() -> new NotFoundException("Caso no encontrado"));
    }

    @Transactional
    public ObsintCaseTask task(UUID taskId) { return owned(taskId).task(taskId); }

    @Transactional
    public ObsintCaseTask start(UUID taskId) {
        ObsintCase c = owned(taskId);
        return c.startTask(c.task(taskId), now());
    }

    @Transactional
    public ObsintCase answer(UUID taskId, ObsintCaseTask.Decision decision, String justification) {
        ObsintCase c = owned(taskId);
        c.answerTask(c.task(taskId), decision, justification, now());
        LOG.infof("obsint_task_answered case=%s task=%s decision=%s actor=%s", c.code, taskId, decision, c.operator);
        return c;
    }

    @Transactional
    public List<ObsintEvidence> evidence(UUID taskId) {
        owned(taskId);
        return ObsintEvidence.list("taskId=?1 order by capturedAt", taskId);
    }

    @Transactional
    public ObsintEvidence addEvidence(UUID taskId, UUID cameraId, double videoTimeSeconds, byte[] image) {
        ObsintCaseTask task = task(taskId);
        task.requireInProgress();
        if (task.consigna.cameras.stream().noneMatch(cam -> cam.id.equals(cameraId))) throw new BadRequestException("La cámara no pertenece a esta tarea.");
        if (ObsintEvidence.count("taskId", taskId) >= MAX_EVIDENCE_PER_TASK) throw new ObsintRuleException("Máximo " + MAX_EVIDENCE_PER_TASK + " evidencias por tarea.");
        if (image.length == 0 || image.length > MAX_EVIDENCE_BYTES) throw new BadRequestException("La imagen debe tener entre 1 byte y 5 MiB.");
        String contentType = platform.imageContentType(image);
        if (contentType == null) throw new BadRequestException("Solo se admiten imágenes JPEG, PNG o WebP.");
        ObsintEvidence e = new ObsintEvidence();
        e.id = UUID.randomUUID();
        e.instanceCountryId = task.instanceCountryId;
        e.taskId = taskId;
        e.cameraId = cameraId;
        e.videoTimeSeconds = Math.max(0, videoTimeSeconds);
        e.capturedAt = now();
        e.bucket = platform.evidenceBucket();
        e.objectKey = "evidence/" + e.instanceCountryId + "/obsint/" + taskId + "/" + e.id + "." + platform.imageExtension(contentType);
        e.contentType = contentType;
        e.sizeBytes = image.length;
        e.sha256 = platform.sha256(image);
        // Primero el archivo y luego la fila: si falla MinIO no queda una fila apuntando a nada.
        platform.put(e.bucket, e.objectKey, image, contentType);
        e.persist();
        return e;
    }

    @Transactional
    public void deleteEvidence(UUID taskId, UUID evidenceId) {
        task(taskId).requireInProgress();
        ObsintEvidence e = evidenceOf(taskId, evidenceId);
        e.delete();
        platform.delete(e.bucket, e.objectKey);
    }

    @Transactional
    public ObsintEvidence evidenceOf(UUID taskId, UUID evidenceId) {
        owned(taskId);
        return ObsintEvidence.<ObsintEvidence>find("id=?1 and taskId=?2", evidenceId, taskId).firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Evidencia no encontrada"));
    }

    @Transactional
    public List<ObsintAgentMessage> messages(UUID taskId) {
        owned(taskId);
        return ObsintAgentMessage.list("taskId=?1 order by sentAt", taskId);
    }

    @Transactional
    public List<ObsintAgentRecipients.Recipient> agentsOnShift(UUID taskId) {
        ObsintCaseTask task=task(taskId);
        return recipients.onShift(task.instanceCountryId,task.consigna.pointId,now());
    }

    /** Compatibilidad con consumidores que envían solamente texto. */
    @Transactional
    public ObsintAgentMessage send(UUID taskId,String text) { return send(taskId,text,"POINT",null); }

    /** Registra el mensaje y la selección validada en una sola transacción. */
    @Transactional
    public ObsintAgentMessage send(UUID taskId,String text,String target,List<UUID> employeeIds) {
        ObsintCaseTask task=task(taskId); task.requireInProgress();
        String value=text==null?"":text.trim();
        if(value.isEmpty()) throw new BadRequestException("Escribe un mensaje antes de enviarlo.");
        if(value.length()>ObsintAgentMessage.TEXT_MAX) throw new BadRequestException("El mensaje admite hasta 500 caracteres.");
        String destination=target==null?"POINT":target;
        if(!Set.of("POINT","AGENTS").contains(destination)) throw new BadRequestException("Destino no válido.");
        List<ObsintAgentRecipients.Recipient> agents=List.of();
        if("AGENTS".equals(destination)) {
            if(employeeIds==null || employeeIds.isEmpty()) throw new BadRequestException("Selecciona al menos un agente.");
            if(employeeIds.stream().anyMatch(java.util.Objects::isNull)) throw new BadRequestException("Agente no válido.");
            Set<UUID> selected=new LinkedHashSet<>(employeeIds);
            if(selected.size()>50) throw new BadRequestException("Selecciona como máximo 50 agentes.");
            var available=recipients.onShift(task.instanceCountryId,task.consigna.pointId,now());
            for(UUID id:selected) if(available.stream().noneMatch(a->a.employeeId().equals(id)))
                throw new BadRequestException(id+" no está de turno en este punto.");
            agents=available.stream().filter(a->selected.contains(a.employeeId())).toList();
        } else if(socket.enabled()) agents=recipients.onShift(task.instanceCountryId,task.consigna.pointId,now());
        ObsintAgentMessage m=new ObsintAgentMessage();
        m.instanceCountryId=task.instanceCountryId; m.taskId=taskId; m.operator=platform.username();
        m.senderRole=monitorScope.cargoNow();
        m.roomId=rooms.monitorConsole(task.instanceCountryId);
        m.text=value; m.sentAt=now(); m.target=destination; m.deliveryStatus=ObsintAgentMessage.STATUS_TEST; m.persist();
        if(socket.enabled()) {
            if(agents.isEmpty()) { m.deliveryStatus="FAILED"; m.failureReason="NO_AGENT_ON_SHIFT"; }
            else {
                for(var agent:agents) {
                    ObsintAgentMessageRecipient r=new ObsintAgentMessageRecipient();
                    r.instanceCountryId=m.instanceCountryId; r.messageId=m.id; r.employeeId=agent.employeeId();
                    r.assignmentId=agent.assignmentId(); r.postId=agent.postId(); r.personaId=agent.personaId();
                    r.fullName=agent.fullName(); r.postName=agent.postName(); r.nextAttemptAt=m.sentAt;
                    r.status=agent.personaId()==null?"FAILED":"PENDING";
                    if(agent.personaId()==null) r.lastError="NO_PERSONA_LINK";
                    r.persist();
                }
                ObsintAgentMessageRecipient.refresh(m);
                if(agents.stream().anyMatch(a->a.personaId()!=null)) queued.fire(new ObsintMessageQueued(m.id));
            }
        }
        LOG.infof("obsint_agent_message case=%s task=%s actor=%s target=%s",task.obsintCase.code,taskId,m.operator,destination);
        return m;
    }

    private ObsintCase owned(UUID taskId) {
        ObsintCaseTask t = ObsintCaseTask.<ObsintCaseTask>find("id=?1 and instanceCountryId=?2 and obsintCase.operator=?3", taskId, platform.instanceCountryId(), platform.username())
            .firstResultOptional().orElseThrow(() -> new NotFoundException("Tarea no encontrada"));
        return t.obsintCase;
    }

    /** Intervalo de cada frecuencia. "Cada turno" se toma como 12 h (turnos de 12 h de la operación) mientras no se use el turno real. */
    static final Map<String, Duration> FREQUENCY_INTERVAL = Map.of("EVERY_SHIFT", Duration.ofHours(12), "EVERY_2_HOURS", Duration.ofHours(2),
        "EVERY_4_HOURS", Duration.ofHours(4));

    /** ¿Toca de nuevo? Nunca respondida, o pasó su intervalo; "Una vez al día" vuelve al día siguiente (hora de Ecuador). */
    static boolean due(String frequency, Instant lastAnswer, Instant now) {
        if (lastAnswer == null) return true;
        if ("DAILY".equals(frequency))
            return !lastAnswer.atZone(ObsintPlatform.ZONE).toLocalDate().equals(now.atZone(ObsintPlatform.ZONE).toLocalDate());
        return !now.isBefore(lastAnswer.plus(FREQUENCY_INTERVAL.getOrDefault(frequency, Duration.ofHours(12))));
    }

    /** Última respuesta por Punto y código de consigna ("pointId|OBS-001"), de cualquier monitorista. */
    private Map<String, Instant> lastAnswers(UUID tenantId, Set<UUID> points) {
        if (points.isEmpty()) return Map.of();
        List<Object[]> rows = em.createQuery("select t.consigna.pointId, t.consignaCode, max(t.answeredAt) from ObsintCaseTask t "
                + "where t.instanceCountryId=:tenant and t.answeredAt is not null and t.consigna.pointId in :points group by t.consigna.pointId, t.consignaCode", Object[].class)
            .setParameter("tenant", tenantId).setParameter("points", points).getResultList();
        Map<String, Instant> out = new java.util.HashMap<>();
        for (Object[] r : rows) out.put(r[0] + "|" + r[1], (Instant) r[2]);
        return out;
    }

    private static String trim(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
}
