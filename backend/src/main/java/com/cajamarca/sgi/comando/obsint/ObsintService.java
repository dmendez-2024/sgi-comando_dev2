package com.cajamarca.sgi.comando.obsint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/** Casos de uso de la Consola OBSINT. Cada monitorista solo ve y modifica sus propios casos. */
@ApplicationScoped
public class ObsintService {
    private static final Logger LOG = Logger.getLogger(ObsintService.class);
    static final int MAX_EVIDENCE_PER_TASK = 6;
    static final long MAX_EVIDENCE_BYTES = 5L * 1024 * 1024;

    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    /** Postgres guarda microsegundos: truncar evita que el deadline cambie al releerlo. */
    public Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }

    /** El caso en curso del monitorista, si lo hay. */
    @Transactional
    public Optional<ObsintCase> current() {
        return ObsintCase.<ObsintCase>find("instanceCountryId=?1 and operator=?2 and status=?3 order by startedAt desc",
            platform.instanceCountryId(), platform.username(), ObsintCase.Status.IN_PROGRESS).firstResultOptional();
    }

    /**
     * Abre un caso con las consignas activas de los protocolos OBSINT ACTIVOS cuyo horario cubre este momento.
     * Si ya hay uno en curso se devuelve ese: un monitorista trabaja un caso a la vez.
     */
    @Transactional
    public ObsintCase open() {
        Optional<ObsintCase> running = current();
        if (running.isPresent()) return running.get();
        UUID tenantId = platform.instanceCountryId();
        ZonedDateTime local = now().atZone(ObsintPlatform.ZONE);
        List<ObsintConsigna> consignas = ObsintConsigna.<ObsintConsigna>list(
                "instanceCountryId=?1 and active=true and protocolId in (select p.id from ObsintProtocol p where p.status='ACTIVO' and p.instanceCountryId=?1) order by pointId, position, code", tenantId)
            .stream().filter(c -> c.scheduledAt(local.getDayOfWeek().getValue(), local.toLocalTime())).toList();
        if (consignas.isEmpty()) throw new ObsintRuleException("No hay consignas OBSINT activas en este horario.");
        long number = ((Number) em.createNativeQuery("select nextval('obsint_case_code_seq')").getSingleResult()).longValue();
        ObsintCase c = ObsintCase.open(tenantId, "CAJ-OBS-%03d".formatted(number), platform.username(), consignas, now());
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

    /** Hoy el mensaje solo se registra: el envío al agente en sitio (SGI Operador) aún no tiene interconexión aprobada. */
    @Transactional
    public ObsintAgentMessage send(UUID taskId, String text) {
        ObsintCaseTask task = task(taskId);
        task.requireInProgress();
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) throw new BadRequestException("Escribe un mensaje antes de enviarlo.");
        if (value.length() > ObsintAgentMessage.TEXT_MAX) throw new BadRequestException("El mensaje admite hasta " + ObsintAgentMessage.TEXT_MAX + " caracteres.");
        ObsintAgentMessage m = new ObsintAgentMessage();
        m.instanceCountryId = task.instanceCountryId;
        m.taskId = taskId;
        m.operator = platform.username();
        m.text = value;
        m.sentAt = now();
        m.deliveryStatus = "SENT";
        m.persist();
        LOG.infof("obsint_agent_message case=%s task=%s actor=%s", task.obsintCase.code, taskId, m.operator);
        return m;
    }

    private ObsintCase owned(UUID taskId) {
        ObsintCaseTask t = ObsintCaseTask.<ObsintCaseTask>find("id=?1 and instanceCountryId=?2 and obsintCase.operator=?3", taskId, platform.instanceCountryId(), platform.username())
            .firstResultOptional().orElseThrow(() -> new NotFoundException("Tarea no encontrada"));
        return t.obsintCase;
    }
}
