package com.cajamarca.sgi.comando.supervision;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.*;
import java.util.*;

/** Consulta (solo lectura) de las evaluaciones que los Supervisores registran desde SGI Operador (V81), acotada al tenant y al alcance territorial del usuario. */
@Path("/api/supervision/evaluations") @Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
public class SupervisionQueryResource {
    private static final ZoneId OPERATING_ZONE = ZoneId.of("America/Guayaquil");
    public record EvaluationRow(UUID id, Instant capturedAt, UUID companyId, String companyName, UUID pointId, String pointName, String clientName,
                                UUID postId, String postCode, String postName, UUID supervisorEmployeeId, String supervisorName, UUID agentEmployeeId, String agentName,
                                String vigilanceLevel, String cleanlinessLevel, String consignmentKnowledgeLevel, String situationalLevel, String inventoryOverallState, String status) {}
    public record EvaluationPage(List<EvaluationRow> items, long total, int page, int size) {}
    public record InventoryItem(UUID assetId, String description, String observedState, String comment) {}
    public record NoveltyDecision(UUID noveltyId, String noveltyType, String title, String severity, Instant occurredAt, String decision, String decidedBy, Instant decidedAt) {}
    public record EvaluationDetail(EvaluationRow row, String supervisorComments, String pointPostNotes, String inventoryComments, UUID consignmentId, Integer consignmentVersion,
                                   String consignmentSnapshot, boolean hasPhoto, String createdBy, Instant createdAt, List<InventoryItem> inventory, List<NoveltyDecision> decisions) {}
    public record Option(UUID id, String name, UUID parentId) {}
    public record FilterOptions(List<Option> companies, List<Option> points, List<Option> posts, List<Option> supervisors, List<Option> agents) {}

    private static final String FROM = "\n" + """
         from supervisor_evaluation e
         join point p on p.id=e.point_id and p.instance_country_id=e.instance_country_id
         join post po on po.id=e.post_id and po.instance_country_id=e.instance_country_id
         left join company co on co.id=p.company_id and co.instance_country_id=e.instance_country_id
         left join employee_operational_snapshot se on se.instance_country_id=e.instance_country_id and se.employee_id=e.supervisor_employee_id
         left join employee_operational_snapshot ae on ae.instance_country_id=e.instance_country_id and ae.employee_id=e.agent_employee_id
         where e.instance_country_id=:tenant and p.company_id in (:companies)""";
    private static final String ROW = """
         select e.id,e.captured_at,p.company_id,co.name,e.point_id,p.name,p.client_name,e.post_id,po.code,po.name,
                e.supervisor_employee_id,coalesce(se.full_name,e.created_by),e.agent_employee_id,coalesce(ae.full_name,'Agente sin ficha'),
                e.vigilance_level,e.cleanliness_level,e.consignment_knowledge_level,e.situational_level,e.inventory_overall_state,e.status""";

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject StorageService storage;

    @GET
    public EvaluationPage list(@QueryParam("companyId") UUID companyId, @QueryParam("pointId") UUID pointId, @QueryParam("postId") UUID postId,
                               @QueryParam("supervisorEmployeeId") UUID supervisorId, @QueryParam("agentEmployeeId") UUID agentId,
                               @QueryParam("from") String from, @QueryParam("to") String to,
                               @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("50") int size) {
        Set<UUID> companies = scope.allowedCompanyIds(); int p = Math.max(0, page), s = Math.max(1, Math.min(200, size));
        if (companies.isEmpty()) return new EvaluationPage(List.of(), 0, p, s);
        LocalDate fromDate = date(from, "from"), toDate = date(to, "to");
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) throw new BadRequestException("La fecha fin no puede ser menor que la fecha inicio");
        Map<String,Object> params = new HashMap<>(Map.of("tenant", tenant.instanceCountryId(), "companies", companies)); StringBuilder where = new StringBuilder();
        filter(where, params, "p.company_id", "company", companyId); filter(where, params, "e.point_id", "point", pointId); filter(where, params, "e.post_id", "post", postId);
        filter(where, params, "e.supervisor_employee_id", "supervisor", supervisorId); filter(where, params, "e.agent_employee_id", "agent", agentId);
        if (fromDate != null) { where.append(" and e.captured_at>=:fromAt"); params.put("fromAt", fromDate.atStartOfDay(OPERATING_ZONE).toInstant()); }
        if (toDate != null) { where.append(" and e.captured_at<:toAt"); params.put("toAt", toDate.plusDays(1).atStartOfDay(OPERATING_ZONE).toInstant()); }
        long total = ((Number) bind(em.createNativeQuery("select count(*)" + FROM + where), params).getSingleResult()).longValue();
        @SuppressWarnings("unchecked") List<Object[]> rows = bind(em.createNativeQuery(ROW + FROM + where + " order by e.captured_at desc, e.id"), params)
            .setFirstResult(p * s).setMaxResults(s).getResultList();
        return new EvaluationPage(rows.stream().map(SupervisionQueryResource::row).toList(), total, p, s);
    }

    /** Compañías, Puntos, Puestos, Supervisores y Agentes que tienen evaluaciones dentro del alcance, para los filtros de la pantalla. */
    @GET @Path("/filters")
    public FilterOptions filters() {
        Set<UUID> companies = scope.allowedCompanyIds();
        if (companies.isEmpty()) return new FilterOptions(List.of(), List.of(), List.of(), List.of(), List.of());
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("""
            select distinct p.company_id,co.name,e.point_id,p.name,e.post_id,po.code||' · '||po.name,e.supervisor_employee_id,coalesce(se.full_name,e.created_by),e.agent_employee_id,coalesce(ae.full_name,'Agente sin ficha')""" + FROM)
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("companies", companies).getResultList();
        Map<UUID,Option> c = new LinkedHashMap<>(), pt = new LinkedHashMap<>(), ps = new LinkedHashMap<>(), sv = new LinkedHashMap<>(), ag = new LinkedHashMap<>();
        for (Object[] r : rows) {
            c.putIfAbsent((UUID) r[0], new Option((UUID) r[0], str(r[1]), null)); pt.putIfAbsent((UUID) r[2], new Option((UUID) r[2], str(r[3]), (UUID) r[0]));
            ps.putIfAbsent((UUID) r[4], new Option((UUID) r[4], str(r[5]), (UUID) r[2])); sv.putIfAbsent((UUID) r[6], new Option((UUID) r[6], str(r[7]), null));
            ag.putIfAbsent((UUID) r[8], new Option((UUID) r[8], str(r[9]), null));
        }
        return new FilterOptions(sorted(c), sorted(pt), sorted(ps), sorted(sv), sorted(ag));
    }

    @GET @Path("/{id}")
    public EvaluationDetail detail(@PathParam("id") UUID id) {
        EvaluationRow row = find(id);
        Object[] e = (Object[]) em.createNativeQuery("""
            select supervisor_comments,point_post_notes,inventory_comments,consignment_id,consignment_version,consignment_snapshot,agent_photo_evidence_id,created_by,created_at
            from supervisor_evaluation where id=:id and instance_country_id=:tenant""").setParameter("id", id).setParameter("tenant", tenant.instanceCountryId()).getSingleResult();
        @SuppressWarnings("unchecked") List<Object[]> items = em.createNativeQuery("""
            select asset_id,description_snapshot,observed_state,comment from supervisor_evaluation_inventory_item where evaluation_id=:id order by description_snapshot""")
            .setParameter("id", id).getResultList();
        @SuppressWarnings("unchecked") List<Object[]> decisions = em.createNativeQuery("""
            select d.novelty_id,d.novelty_type,r.title,r.severity,r.occurred_at,d.decision,d.decided_by,d.decided_at
            from supervisor_evaluation_novelty_decision d left join operator_operational_report r on r.id=d.novelty_id and r.instance_country_id=:tenant
            where d.evaluation_id=:id order by r.occurred_at desc nulls last""").setParameter("id", id).setParameter("tenant", tenant.instanceCountryId()).getResultList();
        return new EvaluationDetail(row, str(e[0]), str(e[1]), str(e[2]), (UUID) e[3], e[4] == null ? null : ((Number) e[4]).intValue(), str(e[5]), e[6] != null, str(e[7]), instant(e[8]),
            items.stream().map(x -> new InventoryItem((UUID) x[0], str(x[1]), str(x[2]), str(x[3]))).toList(),
            decisions.stream().map(x -> new NoveltyDecision((UUID) x[0], str(x[1]), x[2] == null ? "Novedad no disponible" : str(x[2]), str(x[3]), instant(x[4]), str(x[5]), str(x[6]), instant(x[7]))).toList());
    }

    /** Foto actual del Agente tomada por el Supervisor (evidence_object SUPERVISOR_AGENT_PHOTO en MinIO). */
    @GET @Path("/{id}/photo")
    public Response photo(@PathParam("id") UUID id) {
        find(id);
        List<?> ids = em.createNativeQuery("select agent_photo_evidence_id from supervisor_evaluation where id=:id and instance_country_id=:tenant")
            .setParameter("id", id).setParameter("tenant", tenant.instanceCountryId()).getResultList();
        EvidenceObject o = ids.isEmpty() || ids.get(0) == null ? null : EvidenceObject.find("id=?1 and instanceCountryId=?2 and targetType=?3", ids.get(0), tenant.instanceCountryId(), "SUPERVISOR_AGENT_PHOTO").firstResult();
        if (o == null) throw new NotFoundException("Foto no encontrada");
        return Response.ok(storage.read(o.bucket, o.objectKey)).type(o.contentType).header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").build();
    }

    /** Evaluación del tenant; 404 si no existe y 403 si su Compañía está fuera del alcance del usuario. */
    private EvaluationRow find(UUID id) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(ROW + FROM.replace(" and p.company_id in (:companies)", "") + " and e.id=:id")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("id", id).getResultList();
        if (rows.isEmpty()) throw new NotFoundException("Evaluación no encontrada");
        EvaluationRow row = row(rows.get(0)); scope.requireCompany(row.companyId()); return row;
    }

    private static EvaluationRow row(Object[] r) {
        return new EvaluationRow((UUID) r[0], instant(r[1]), (UUID) r[2], str(r[3]), (UUID) r[4], str(r[5]), str(r[6]), (UUID) r[7], str(r[8]), str(r[9]),
            (UUID) r[10], str(r[11]), (UUID) r[12], str(r[13]), str(r[14]), str(r[15]), str(r[16]), str(r[17]), str(r[18]), str(r[19]));
    }
    private static void filter(StringBuilder where, Map<String,Object> params, String column, String name, UUID value) { if (value != null) { where.append(" and ").append(column).append("=:").append(name); params.put(name, value); } }
    private static Query bind(Query q, Map<String,Object> params) { params.forEach(q::setParameter); return q; }
    private static List<Option> sorted(Map<UUID,Option> m) { return m.values().stream().sorted(Comparator.comparing(o -> o.name() == null ? "" : o.name(), String.CASE_INSENSITIVE_ORDER)).toList(); }
    private static LocalDate date(String v, String field) { try { return v == null || v.isBlank() ? null : LocalDate.parse(v.trim()); } catch (Exception e) { throw new BadRequestException(field + " debe tener formato AAAA-MM-DD"); } }
    private static String str(Object v) { return v == null ? null : v.toString(); }
    private static Instant instant(Object v) {
        if (v == null) return null; if (v instanceof Instant i) return i; if (v instanceof OffsetDateTime o) return o.toInstant();
        if (v instanceof java.sql.Timestamp t) return t.toInstant(); return Instant.parse(v.toString());
    }
}
