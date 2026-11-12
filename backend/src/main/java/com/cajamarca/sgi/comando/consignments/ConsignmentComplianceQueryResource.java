package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Consulta de ejecuciones de consignas configuradas; las propuestas ad-hoc tienen otro flujo. */
@Path("/api/consignments/compliances")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ConsignmentComplianceQueryResource {
    private static final ZoneId OPERATING_ZONE = ZoneId.of("America/Guayaquil");
    @Inject EntityManager em;
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject ObjectMapper mapper;

    @GET
    public ArrayNode completed(@QueryParam("dateFrom") String dateFrom, @QueryParam("dateTo") String dateTo) {
        LocalDate from;
        LocalDate to;
        try {
            to = dateTo == null || dateTo.isBlank() ? LocalDate.now(OPERATING_ZONE) : LocalDate.parse(dateTo);
            from = dateFrom == null || dateFrom.isBlank() ? to.minusDays(30) : LocalDate.parse(dateFrom);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Las fechas de consulta no son válidas");
        }
        if (from.isAfter(to) || from.plusYears(1).isBefore(to))
            throw new BadRequestException("El rango de consulta no puede superar un año");
        Set<UUID> companies = scope.allowedCompanyIdsForCompanyRoles();
        ArrayNode result = mapper.createArrayNode();
        if (companies.isEmpty()) return result;
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("""
            select cc.id,c.code,c.title,c.instruction,cp.code,cp.name,c.priority,
                   coalesce(e.full_name,cc.username),cc.username,cc.executed_at,cc.confirmed_at,
                   cc.comment,cc.photo_count,pt.client_name,pt.city,pt.name,po.name,
                   company.code,company.name
            from consignment_compliance cc
            join consignment c on c.id=cc.consignment_id and c.instance_country_id=cc.instance_country_id
            join consignment_protocol cp on cp.id=cc.protocol_id and cp.instance_country_id=cc.instance_country_id
            join operational_assignment a on a.id=cc.assignment_id and a.instance_country_id=cc.instance_country_id
            join shift_occurrence sh on sh.id=a.shift_occurrence_id and sh.instance_country_id=a.instance_country_id
            join post po on po.id=sh.post_id and po.instance_country_id=sh.instance_country_id
            join point pt on pt.id=po.point_id and pt.instance_country_id=po.instance_country_id
            left join company on company.id=pt.company_id and company.instance_country_id=pt.instance_country_id
            left join employee_operational_snapshot e on e.employee_id=cc.employee_id and e.instance_country_id=cc.instance_country_id
            where cc.instance_country_id=:tenant and cc.result='CUMPLIDA' and pt.company_id in (:companies)
              and cc.confirmed_at>=:from and cc.confirmed_at<:to
            order by cc.confirmed_at desc
            """).setParameter("tenant", tenant.instanceCountryId()).setParameter("companies", companies)
                .setParameter("from", from.atStartOfDay(OPERATING_ZONE).toInstant())
                .setParameter("to", to.plusDays(1).atStartOfDay(OPERATING_ZONE).toInstant())
                .getResultList();
        for (Object[] row : rows) {
            ObjectNode item = result.addObject()
                .put("id", row[0].toString()).put("code", row[1].toString())
                .put("title", row[2].toString()).put("instruction", row[3].toString())
                .put("protocolCode", row[4].toString()).put("protocolName", row[5].toString())
                .put("priority", row[6].toString()).put("responsible", row[7].toString())
                .put("username", row[8].toString()).put("executedAt", row[9] == null ? row[10].toString() : row[9].toString())
                .put("confirmedAt", row[10].toString()).put("comment", row[11] == null ? "" : row[11].toString())
                .put("photoCount", ((Number) row[12]).intValue())
                .put("clientName", row[13] == null ? "" : row[13].toString())
                .put("city", row[14] == null ? "" : row[14].toString())
                .put("pointName", row[15].toString()).put("postName", row[16].toString())
                .put("companyCode", row[17] == null ? "" : row[17].toString())
                .put("companyName", row[18] == null ? "" : row[18].toString());
        }
        return result;
    }
}
