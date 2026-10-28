package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.operations.PointEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reportes de casos OBSINT finalizados. El control de acceso se aplica en cada solicitud. */
@Path("/api/obsint/reports")
@RolesAllowed({"PRESIDENTE", "DIRECTOR_NACIONAL"})
public class ObsintReportResource {
    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    @Inject ObsintPlatform platform;

    public record ReportView(UUID caseId, UUID pointId, String code, String client, String point,
                             Instant completedAt, int tasks, int answered, int timedOut) {}

    @GET @Produces(MediaType.APPLICATION_JSON) @Transactional
    public List<ReportView> list() {
        List<ObsintCase> cases = ObsintCase.<ObsintCase>list("instanceCountryId=?1 and status=?2 order by completedAt desc",
            platform.instanceCountryId(), ObsintCase.Status.COMPLETED);
        List<ReportView> reports = new ArrayList<>();
        for (ObsintCase obsCase : cases) {
            Map<UUID, List<ObsintCaseTask>> byPoint = new LinkedHashMap<>();
            for (ObsintCaseTask task : obsCase.tasks)
                byPoint.computeIfAbsent(task.consigna.pointId, ignored -> new ArrayList<>()).add(task);
            for (Map.Entry<UUID, List<ObsintCaseTask>> entry : byPoint.entrySet()) {
                PointEntity point = point(entry.getKey());
                if (point == null) continue;
                List<ObsintCaseTask> tasks = entry.getValue();
                reports.add(new ReportView(obsCase.id, point.id, obsCase.code, point.clientName, point.name,
                    obsCase.completedAt, tasks.size(), (int) tasks.stream().filter(t -> t.decision != null).count(),
                    (int) tasks.stream().filter(t -> Boolean.TRUE.equals(t.timedOut)).count()));
            }
        }
        reports.sort(Comparator.comparing(ReportView::completedAt).reversed());
        return reports;
    }

    @GET @Path("/{caseId}/points/{pointId}/pdf") @Produces("application/pdf") @Transactional
    public Response pdf(@PathParam("caseId") UUID caseId, @PathParam("pointId") UUID pointId) {
        ObsintCase obsCase = ObsintCase.<ObsintCase>find("id=?1 and instanceCountryId=?2 and status=?3",
            caseId, platform.instanceCountryId(), ObsintCase.Status.COMPLETED).firstResult();
        PointEntity point = point(pointId);
        if (obsCase == null || point == null) throw new NotFoundException("Reporte OBSINT no encontrado");
        List<ObsintCaseTask> tasks = obsCase.tasks.stream().filter(t -> pointId.equals(t.consigna.pointId)).toList();
        if (tasks.isEmpty()) throw new NotFoundException("Reporte OBSINT no encontrado");
        List<String> lines = new ArrayList<>(List.of("REPORTE OBSINT", "Caso: " + obsCase.code,
            "Cliente: " + point.clientName, "Punto: " + point.name,
            "Finalizado: " + DATE.format(obsCase.completedAt), "Monitorista: " + obsCase.operator, " "));
        for (ObsintCaseTask task : tasks) {
            lines.add("Tarea " + task.position + " - " + task.consignaCode + " - " + task.consigna.title);
            lines.add("Resultado: " + (task.decision == null ? "Sin respuesta" : task.decision.name())
                + (Boolean.TRUE.equals(task.timedOut) ? " (fuera de tiempo)" : ""));
            lines.add("Justificacion: " + (task.justification == null ? "" : task.justification));
            lines.add(" ");
        }
        byte[] pdf = simplePdf(lines);
        return Response.ok(pdf).type("application/pdf")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=obsint-" + obsCase.code + "-" + point.code + ".pdf")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }

    private PointEntity point(UUID id) {
        return PointEntity.<PointEntity>find("id=?1 and instanceCountryId=?2", id, platform.instanceCountryId()).firstResult();
    }

    /** PDF de texto sencillo, sin dependencia adicional de generación de documentos. */
    private static byte[] simplePdf(List<String> lines) {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        int pages = Math.max(1, (lines.size() + 39) / 40);
        StringBuilder kids = new StringBuilder();
        for (int page = 0; page < pages; page++) kids.append(4 + page * 2).append(" 0 R ");
        objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        for (int page = 0; page < pages; page++) {
            int pageObject = 4 + page * 2;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents " + (pageObject + 1) + " 0 R >>");
            StringBuilder stream = new StringBuilder("BT /F1 11 Tf 44 790 Td 14 TL ");
            for (int i = page * 40; i < Math.min(lines.size(), (page + 1) * 40); i++) {
                String text = Normalizer.normalize(lines.get(i), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .replaceAll("[^\\x20-\\x7E]", "?");
                if (text.length() > 95) text = text.substring(0, 95);
                stream.append('(').append(text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"))
                    .append(") Tj T* ");
            }
            stream.append("ET");
            byte[] content = stream.toString().getBytes(StandardCharsets.US_ASCII);
            objects.add("<< /Length " + content.length + " >>\nstream\n" + stream + "\nendstream");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>(List.of(0));
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) write(out, "%010d 00000 n \n".formatted(offsets.get(i)));
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
