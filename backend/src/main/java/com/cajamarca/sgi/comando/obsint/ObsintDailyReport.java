package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.secelec.SecElecComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * "Informe Diario de Consignas" (diapositiva 13): todo lo que el monitorista respondió en un Punto durante un día (00:00–24:00,
 * America/Guayaquil), sin importar en cuántos casos. Dos indicadores: cumplimiento = Sí / (Sí + No), sobre lo que se pudo verificar,
 * y verificabilidad = (Sí + No) / ejecutadas, cuánto se pudo verificar por cámara (Incierto la baja).
 */
@ApplicationScoped
public class ObsintDailyReport {
    /** Nombre del producto OBSINT (diapositiva 2 de "OBSINT Estructura"); va en "Servicio" del informe, no el servicio de guardias del Punto. */
    static final String PRODUCT_NAME = "Servicio de Auditoría OBSINT";
    private static final Logger LOG = Logger.getLogger(ObsintDailyReport.class);
    static final Map<String, String> CATEGORY = Map.of("SSO", "Seguridad y salud ocupacional", "PRODUCTION", "Producción industrial",
        "LOGISTICS", "Logística y almacenamiento", "CLEANING", "Orden y limpieza", "MOBILITY", "Movilidad y tránsito interno",
        "CUSTOMER_SERVICE", "Atención al cliente");
    static final Map<String, String> FREQUENCY = Map.of("EVERY_SHIFT", "Cada turno", "EVERY_2_HOURS", "Cada 2 horas",
        "EVERY_4_HOURS", "Cada 4 horas", "DAILY", "Una vez al día");

    @Inject ObsintPlatform platform;

    /** Fila de la lista de Reportes: un Punto en un día. compliance null = solo hubo respuestas Incierto. */
    public record Row(UUID pointId, LocalDate date, String client, String point, int executed, Double compliance) {}
    public record Summary(int configured, int executed, int yes, int no, int uncertain, Double compliance, Double verifiability, Integer averageSeconds) {}
    public record ConsignaLine(String code, String title, String category, String frequency, int executed, int yes, int no, int uncertain, Double compliance) {}
    /** photo/photoType null = el monitorista no capturó evidencia. */
    public record Failure(String code, String title, Instant answeredAt, String location, String observation, byte[] photo, String photoType) {}
    public record Report(LocalDate date, String client, String point, String service, Instant generatedAt, Summary summary,
                         List<ConsignaLine> consignas, List<Failure> failures, String observations) {}

    /** Filas de la lista entre esas fechas (incluidas), la más reciente primero. */
    public List<Row> rows(LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, List<ObsintCaseTask>>> grouped = new HashMap<>();
        for (ObsintCaseTask t : answered(from, to))
            grouped.computeIfAbsent(t.consigna.pointId, k -> new HashMap<>()).computeIfAbsent(day(t.answeredAt), k -> new ArrayList<>()).add(t);
        Map<UUID, PointEntity> points = points(grouped.keySet());
        List<Row> rows = new ArrayList<>();
        grouped.forEach((pointId, byDay) -> {
            PointEntity p = points.get(pointId);
            if (p == null) return;
            byDay.forEach((date, tasks) -> rows.add(new Row(pointId, date, p.clientName, p.name, tasks.size(), compliance(tasks))));
        });
        rows.sort(Comparator.comparing(Row::date).reversed().thenComparing(Row::client).thenComparing(Row::point));
        return rows;
    }

    /** Informe de un Punto en un día; Optional.empty() si ese día no hubo respuestas. */
    public Optional<Report> report(UUID pointId, LocalDate date) {
        PointEntity point = points(Set.of(pointId)).get(pointId);
        if (point == null) return Optional.empty();
        List<ObsintCaseTask> tasks = answered(date, date).stream().filter(t -> pointId.equals(t.consigna.pointId))
            .sorted(Comparator.comparing(t -> t.answeredAt)).toList();
        if (tasks.isEmpty()) return Optional.empty();

        int yes = count(tasks, ObsintCaseTask.Decision.YES), no = count(tasks, ObsintCaseTask.Decision.NO);
        OptionalDouble avg = tasks.stream().filter(t -> t.secondsUsed != null).mapToInt(t -> t.secondsUsed).average();
        Summary summary = new Summary(configured(pointId), tasks.size(), yes, no, count(tasks, ObsintCaseTask.Decision.UNCERTAIN),
            compliance(tasks), verifiability(tasks), avg.isPresent() ? (int) Math.round(avg.getAsDouble()) : null);

        Map<String, List<ObsintCaseTask>> byCode = tasks.stream().collect(Collectors.groupingBy(t -> t.consignaCode, TreeMap::new, Collectors.toList()));
        List<ConsignaLine> lines = byCode.values().stream().map(list -> {
            ObsintConsigna c = list.get(list.size() - 1).consigna;
            return new ConsignaLine(c.code, c.title, CATEGORY.getOrDefault(c.category, c.category), FREQUENCY.getOrDefault(c.frequency, c.frequency),
                list.size(), count(list, ObsintCaseTask.Decision.YES), count(list, ObsintCaseTask.Decision.NO),
                count(list, ObsintCaseTask.Decision.UNCERTAIN), compliance(list));
        }).toList();

        List<Failure> failures = tasks.stream().filter(t -> t.decision == ObsintCaseTask.Decision.NO).map(this::failure).toList();
        return Optional.of(new Report(date, point.clientName, point.name, PRODUCT_NAME, Instant.now(),
            summary, lines, failures, observations(summary, lines)));
    }

    private Failure failure(ObsintCaseTask t) {
        ObsintEvidence evidence = ObsintEvidence.<ObsintEvidence>find("taskId=?1 order by capturedAt", t.id).firstResult();
        SecElecComponent camera = evidence == null ? t.consigna.cameras.stream().findFirst().orElse(null)
            : SecElecComponent.<SecElecComponent>findById(evidence.cameraId);
        byte[] photo = null;
        if (evidence != null) {
            try { photo = platform.read(evidence.bucket, evidence.objectKey); }
            catch (RuntimeException e) { LOG.warnf("obsint_report_photo_unavailable task=%s key=%s", t.id, evidence.objectKey); }
        }
        String location = camera == null ? "—" : (camera.location == null || camera.location.isBlank() ? camera.name : camera.location) + " - " + camera.code;
        return new Failure(t.consignaCode, t.consigna.title, t.answeredAt, location, t.justification == null ? "" : t.justification,
            photo, photo == null ? null : evidence.contentType);
    }

    /** Texto automático mientras no exista un campo para que alguien lo escriba. */
    static String observations(Summary s, List<ConsignaLine> lines) {
        StringBuilder out = new StringBuilder();
        if (s.compliance() == null) out.append("No hubo verificaciones con resultado Sí o No en el día.");
        else out.append("Durante el día se registró un cumplimiento de %s %% (%d de %d verificaciones con resultado Sí o No)."
            .formatted(percent(s.compliance()), s.yes(), s.yes() + s.no()));
        List<ConsignaLine> failed = lines.stream().filter(l -> l.no() > 0).sorted(Comparator.comparingInt(ConsignaLine::no).reversed()).toList();
        if (!failed.isEmpty()) out.append(" Los incumplimientos se concentran en: ")
            .append(failed.stream().limit(3).map(l -> l.title() + " (" + l.no() + ")").collect(Collectors.joining(", "))).append('.');
        if (s.uncertain() > 0) out.append(s.uncertain() == 1 ? " 1 verificación quedó como Incierto" : " %d verificaciones quedaron como Incierto".formatted(s.uncertain()))
            .append(" (no se pudo confirmar por cámara): verificabilidad de %s %%".formatted(percent(s.verifiability())))
            .append(s.verifiability() < LOW_VERIFIABILITY ? "; está por debajo del %d %%, revise la visibilidad de las cámaras.".formatted(Math.round(LOW_VERIFIABILITY * 100)) : ".");
        return out.toString();
    }

    /** Por debajo de esto la verificabilidad se marca como alerta (problemas de cámara). */
    static final double LOW_VERIFIABILITY = 0.9;

    /** (Sí + No) / ejecutadas; null si no hubo respuestas. */
    static Double verifiability(List<ObsintCaseTask> tasks) {
        return tasks.isEmpty() ? null : (double) (count(tasks, ObsintCaseTask.Decision.YES) + count(tasks, ObsintCaseTask.Decision.NO)) / tasks.size();
    }

    static String percent(double value) { return String.format(Locale.ROOT, "%.1f", value * 100); }

    /** Cumplidas / (cumplidas + incumplidas); null si no hubo ninguna de las dos. */
    static Double compliance(List<ObsintCaseTask> tasks) {
        int yes = count(tasks, ObsintCaseTask.Decision.YES), no = count(tasks, ObsintCaseTask.Decision.NO);
        return yes + no == 0 ? null : (double) yes / (yes + no);
    }

    private static int count(List<ObsintCaseTask> tasks, ObsintCaseTask.Decision decision) {
        return (int) tasks.stream().filter(t -> t.decision == decision).count();
    }

    private static LocalDate day(Instant at) { return at.atZone(ObsintPlatform.ZONE).toLocalDate(); }

    private List<ObsintCaseTask> answered(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ObsintPlatform.ZONE).toInstant(), end = to.plusDays(1).atStartOfDay(ObsintPlatform.ZONE).toInstant();
        return ObsintCaseTask.list("instanceCountryId=?1 and answeredAt>=?2 and answeredAt<?3", platform.instanceCountryId(), start, end);
    }

    private Map<UUID, PointEntity> points(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return PointEntity.<PointEntity>list("instanceCountryId=?1 and id in ?2", platform.instanceCountryId(), ids).stream()
            .collect(Collectors.toMap(p -> p.id, p -> p));
    }

    /** Consignas activas del protocolo OBSINT activo del Punto (lo configurado hoy). */
    private int configured(UUID pointId) {
        return (int) ObsintConsigna.count("instanceCountryId=?1 and pointId=?2 and active=true and protocolId in "
            + "(select p.id from ObsintProtocol p where p.status='ACTIVO' and p.pointId=?2)", platform.instanceCountryId(), pointId);
    }
}
