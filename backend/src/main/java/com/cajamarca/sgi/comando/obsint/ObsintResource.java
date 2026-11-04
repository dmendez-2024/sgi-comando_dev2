package com.cajamarca.sgi.comando.obsint;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Consola OBSINT: caso del monitorista, tareas en orden con tiempo máximo, evidencia por cámara y mensajes al agente.
 * MONITOR (cargos Monitor y Agente de Consola de Monitoreo Senior en IDENT) entra solo aquí, no a la configuración.
 */
@Path("/api/obsint")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD",ObsintPlatform.MONITOR_ROLE})
public class ObsintResource {
    @Inject ObsintService service;
    @Inject ObsintPlatform platform;

    /** Igual que el resto de Comando: el motivo del rechazo viaja como {"message": …} para que la pantalla lo muestre. */
    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return ObsintPlatform.errorResponse(e);
    }

    public record CameraView(UUID id, String code, String name, String location, String streamUrl, boolean live) {
        static CameraView of(com.cajamarca.sgi.comando.secelec.SecElecComponent c) { return new CameraView(c.id, c.code, c.name, c.location, c.streamUrl, false); }
    }
    public record TaskSummary(UUID id, int position, ObsintCaseTask.Status status, ObsintCaseTask.Decision decision, Boolean timedOut) {
        static TaskSummary of(ObsintCaseTask t) { return new TaskSummary(t.id, t.position, t.status, t.decision, t.timedOut); }
    }
    public record CaseView(UUID id, String code, String operator, ObsintCase.Status status, Instant startedAt, Instant completedAt,
                           UUID currentTaskId, List<TaskSummary> tasks) {
        static CaseView of(ObsintCase c) {
            return new CaseView(c.id, c.code, c.operator, c.status, c.startedAt, c.completedAt,
                c.currentTask().map(t -> t.id).orElse(null), c.tasks.stream().map(TaskSummary::of).toList());
        }
    }
    /** serverNow permite a la web corregir la diferencia entre su reloj y el del servidor al calcular el tiempo restante. */
    public record TaskView(UUID id, UUID caseId, String caseCode, String operator, int position, int totalTasks, String consignaCode,
                           String instruction, int timeLimitSeconds, ObsintCaseTask.Status status, Instant startedAt, Instant deadline,
                           Instant serverNow, ObsintCaseTask.Decision decision, String justification, Boolean timedOut, List<CameraView> cameras) {
        static TaskView of(ObsintCaseTask t, Instant now) {
            ObsintCase c = t.obsintCase;
            return new TaskView(t.id, c.id, c.code, c.operator, t.position, c.tasks.size(), t.consignaCode, t.instruction, t.timeLimitSeconds,
                t.status, t.startedAt, t.deadline(), now, t.decision, t.justification, t.timedOut, t.consigna.cameras.stream().map(CameraView::of).toList());
        }
    }
    public record AnswerRequest(ObsintCaseTask.Decision decision, String justification) {}
    public record EvidenceView(UUID id, UUID cameraId, double videoTimeSeconds, Instant capturedAt, String contentType, long sizeBytes) {
        static EvidenceView of(ObsintEvidence e) { return new EvidenceView(e.id, e.cameraId, e.videoTimeSeconds, e.capturedAt, e.contentType, e.sizeBytes); }
    }
    public record MessageRequest(String text) {}
    public record MessageView(UUID id, String operator, String text, Instant sentAt, String deliveryStatus) {
        static MessageView of(ObsintAgentMessage m) { return new MessageView(m.id, m.operator, m.text, m.sentAt, m.deliveryStatus); }
    }

    /** Caso en curso del monitorista; 204 si no tiene ninguno. */
    /** SEN-01: si el usuario tiene al menos un Punto con orden de servicio OBSINT; la pantalla oculta el botón OBSINT si no. */
    public record AccessView(boolean enabled) {}

    @GET @Path("/access") @Transactional
    public AccessView access() { return new AccessView(!platform.visiblePoints(platform.allEnabledPoints()).isEmpty()); }

    @GET @Path("/cases/current") @Transactional
    public Response current() {
        return service.current().map(c -> Response.ok(CaseView.of(c)).build()).orElseGet(() -> Response.noContent().build());
    }

    /** Abre un caso con las consignas activas (o devuelve el que ya está en curso). El tiempo corre al iniciar cada tarea. */
    @POST @Path("/cases") @Transactional
    public CaseView open() { return CaseView.of(service.open()); }

    @GET @Path("/cases/{caseId}") @Transactional
    public CaseView get(@PathParam("caseId") UUID caseId) { return CaseView.of(service.get(caseId)); }

    @GET @Path("/tasks/{taskId}") @Transactional
    public TaskView task(@PathParam("taskId") UUID taskId) { return TaskView.of(service.task(taskId), service.now()); }

    /** Arranca el tiempo de la tarea. Es idempotente: llamarlo de nuevo devuelve el mismo deadline. */
    @POST @Path("/tasks/{taskId}/start") @Transactional
    public TaskView start(@PathParam("taskId") UUID taskId) { return TaskView.of(service.start(taskId), service.now()); }

    /** Registra la respuesta y devuelve el caso con la siguiente tarea actual (o COMPLETED). */
    @PUT @Path("/tasks/{taskId}/answer") @Transactional @Consumes(MediaType.APPLICATION_JSON)
    public CaseView answer(@PathParam("taskId") UUID taskId, AnswerRequest body) {
        if (body == null) throw new BadRequestException("Respuesta vacía.");
        return CaseView.of(service.answer(taskId, body.decision(), body.justification()));
    }

    @GET @Path("/tasks/{taskId}/evidence")
    public List<EvidenceView> evidence(@PathParam("taskId") UUID taskId) {
        return service.evidence(taskId).stream().map(EvidenceView::of).toList();
    }

    /** multipart/form-data: file (imagen), cameraId, videoTimeSeconds. */
    @POST @Path("/tasks/{taskId}/evidence") @Consumes(MediaType.MULTIPART_FORM_DATA)
    public EvidenceView addEvidence(@PathParam("taskId") UUID taskId, @RestForm("file") FileUpload file,
                                    @RestForm("cameraId") UUID cameraId, @RestForm("videoTimeSeconds") double videoTimeSeconds) throws IOException {
        if (file == null || cameraId == null) throw new BadRequestException("Imagen y cámara son obligatorias.");
        return EvidenceView.of(service.addEvidence(taskId, cameraId, videoTimeSeconds, Files.readAllBytes(file.uploadedFile())));
    }

    @DELETE @Path("/tasks/{taskId}/evidence/{evidenceId}")
    public void deleteEvidence(@PathParam("taskId") UUID taskId, @PathParam("evidenceId") UUID evidenceId) {
        service.deleteEvidence(taskId, evidenceId);
    }

    @GET @Path("/tasks/{taskId}/evidence/{evidenceId}/content") @Produces("image/*")
    public Response evidenceContent(@PathParam("taskId") UUID taskId, @PathParam("evidenceId") UUID evidenceId) {
        ObsintEvidence e = service.evidenceOf(taskId, evidenceId);
        return Response.ok(platform.read(e.bucket, e.objectKey)).type(e.contentType)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store").header("X-Content-Type-Options", "nosniff").build();
    }

    @GET @Path("/tasks/{taskId}/messages")
    public List<MessageView> messages(@PathParam("taskId") UUID taskId) {
        return service.messages(taskId).stream().map(MessageView::of).toList();
    }

    @POST @Path("/tasks/{taskId}/messages") @Consumes(MediaType.APPLICATION_JSON)
    public MessageView send(@PathParam("taskId") UUID taskId, MessageRequest body) {
        return MessageView.of(service.send(taskId, body == null ? null : body.text()));
    }
}
