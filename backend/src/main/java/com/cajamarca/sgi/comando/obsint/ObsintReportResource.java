package com.cajamarca.sgi.comando.obsint;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Reportes OBSINT (diapositivas 12 y 13): un "Informe Diario de Consignas" por Punto y día. Solo Presidente y Director Nacional
 * (nota de las diapositivas 11 y 12). En IDENT el cargo "Director Nacional de Operaciones" llega como DIRECTOR_OPERACIONES_NACIONAL;
 * DIRECTOR_NACIONAL es el perfil UAT.
 */
@Path("/api/obsint/reports")
@RolesAllowed({"PRESIDENTE", "DIRECTOR_NACIONAL", "DIRECTOR_OPERACIONES_NACIONAL"})
public class ObsintReportResource {
    /** Rango máximo de la lista, para no recorrer todo el historial en una consulta. */
    static final int MAX_DAYS = 366;

    @Inject ObsintDailyReport reports;

    public record ReportView(UUID pointId, LocalDate date, String client, String point, int executed, Double compliance) {
        static ReportView of(ObsintDailyReport.Row r) { return new ReportView(r.pointId(), r.date(), r.client(), r.point(), r.executed(), r.compliance()); }
    }

    /** Informes entre from y to (yyyy-MM-dd, incluidos). Por defecto, los últimos 30 días. */
    @GET @Produces(MediaType.APPLICATION_JSON) @Transactional
    public List<ReportView> list(@QueryParam("from") String from, @QueryParam("to") String to) {
        LocalDate end = to == null || to.isBlank() ? LocalDate.now(ObsintPlatform.ZONE) : date(to);
        LocalDate start = from == null || from.isBlank() ? end.minusDays(29) : date(from);
        if (start.isAfter(end)) throw new BadRequestException("La fecha inicial no puede ser posterior a la final.");
        if (start.plusDays(MAX_DAYS).isBefore(end)) throw new BadRequestException("El rango no puede superar " + MAX_DAYS + " días.");
        return reports.rows(start, end).stream().map(ReportView::of).toList();
    }

    @GET @Path("/points/{pointId}/days/{date}/pdf") @Produces("application/pdf") @Transactional
    public Response pdf(@PathParam("pointId") UUID pointId, @PathParam("date") String date) {
        LocalDate day = date(date);
        ObsintDailyReport.Report report = reports.report(pointId, day).orElseThrow(() -> new NotFoundException("Reporte OBSINT no encontrado"));
        String file = "obsint-" + slug(report.point()) + "-" + day + ".pdf";
        return Response.ok(ObsintReportPdf.render(report)).type("application/pdf")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }

    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return Response.status(e.getResponse().getStatus()).entity(Map.of("message", e.getMessage())).type(MediaType.APPLICATION_JSON).build();
    }

    private static LocalDate date(String value) {
        try { return LocalDate.parse(value); } catch (DateTimeParseException e) { throw new BadRequestException("Fecha inválida: use el formato aaaa-mm-dd."); }
    }

    private static String slug(String value) {
        String s = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "punto" : s;
    }
}
