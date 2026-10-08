package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.consignments.Consignment;
import com.cajamarca.sgi.comando.consignments.ConsignmentPostScope;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.outbox.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.panache.common.Page;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import io.quarkus.security.identity.SecurityIdentity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/assignments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class AssignmentResource {
    private static final ZoneId OPERATING_ZONE = ZoneId.of("America/Guayaquil");

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject ObjectMapper mapper;
    @Inject AssignmentScopeService scope;
    @Inject AssignmentRolePolicy rolePolicy;

    public record EnsurePlanRequest(UUID companyId, LocalDate weekStart) {}
    public record AssignRequest(UUID planId, UUID shiftOccurrenceId, UUID employeeId, UUID sourceAssignmentId, String reason) {}
    public record PublishResponse(UUID planId, String status, Instant publishedAt, int requiredShifts, int assignedShifts, int unassignedShifts) {}
    public record PlanDto(UUID id, UUID companyId, LocalDate weekStart, String status, Instant publishedAt, String publishedBy, Instant draftSavedAt, String draftSavedBy) {}
    public record PointDto(UUID id, String code, String name, String clientName, String city) {}
    public record PostDto(UUID id, UUID pointId, String code, String name, String format, BigDecimal fhe, String tier,
                          String rotationCode, Integer cycleLengthDays, String cycleSourceVersion) {}
    public record ShiftDto(UUID id, UUID postId, UUID templateId, LocalDate localDate, String shiftCode, String shiftName, Instant startsAt, Instant endsAt, String commercialVersion) {}
    public record AssignmentDto(UUID id, UUID shiftOccurrenceId, UUID originalEmployeeId, UUID effectiveEmployeeId, String employeeName, String status,
                                BigDecimal ic, BigDecimal idScore, List<String> warnings, String assignedBy, Instant assignedAt,
                                String reassignedBy, Instant reassignedAt, String reassignmentReason) {}
    public record SummaryDto(int requiredShifts, int assignedShifts, int unassignedShifts, double coveragePct, int warningsCount, double plannedIcAvg,
                             Integer publishedRequiredShifts, Integer publishedAssignedShifts, Double publishedCoveragePct) {}
    public record WeekResponse(PlanDto plan, List<PointDto> points, List<PostDto> posts, List<ShiftDto> shifts, List<AssignmentDto> assignments, SummaryDto summary) {}
    public record UnavailabilityDto(String type, Instant startsAt, Instant endsAt, String sourceRef, String sourceReasonLabel) {}
    public record TransferMiniDto(UUID id,String direction,String status,UUID originCompanyId,String originCompanyName,UUID destinationCompanyId,String destinationCompanyName,String reasonCode,String reasonLabel,String observations,String initiatedBy,Instant initiatedAt,Instant effectiveAt,int releasedFutureAssignments) {}
    public record PersonnelDto(UUID employeeId, String fullName, String roleCode, String employmentStatus, BigDecimal idScore, String preferredShift,
                               String photoKey, boolean requiredChange, double assignedHours, List<UnavailabilityDto> unavailability, TransferMiniDto transfer) {}
    public record PersonnelPage(List<PersonnelDto> items, long total, int page, int size) {}
    public record PersonnelRoleDto(String code, String label) {}
    public record SkillDto(String code, String label, BigDecimal level) {}
    public record EmployeeDetails(UUID employeeId, String fullName, String roleCode, String employmentStatus, BigDecimal idScore, String preferredShift,
                                  String photoKey, boolean requiredChange, UUID companyId, String companyName, List<SkillDto> skills, List<UnavailabilityDto> unavailability, List<AssignmentAuditDto> recentAssignments, TransferMiniDto transfer) {}
    public record TransferReasonDto(String code,String label) {}
    public record TransferDestinationDto(UUID id,String code,String name,String companyType,boolean alwaysActive) {}
    public record TransferRequest(UUID employeeId,UUID destinationCompanyId,String reasonCode,String observations) {}
    public record TransferDecisionRequest(String note) {}
    public record CompanyTransferDto(UUID id,UUID employeeId,String employeeName,String roleCode,BigDecimal idScore,UUID originCompanyId,String originCompanyName,UUID destinationCompanyId,String destinationCompanyName,String reasonCode,String reasonLabel,String observations,String status,String initiatedBy,Instant initiatedAt,String decisionBy,Instant decisionAt,String decisionNote,Instant effectiveAt,int releasedFutureAssignments,String rrhhSyncStatus) {}
    public record AssignmentAuditDto(String postCode, String postName, String shiftName, Instant startsAt, String assignmentStatus, String actor) {}
    public record CoverageCompanyDto(UUID companyId, String companyName, int posts, int requiredShifts, int assignedShifts, int unassignedShifts, double coveragePct,
                                     Integer publishedRequiredShifts, Integer publishedAssignedShifts, Double publishedCoveragePct, String planStatus) {}
    public record CoverageResponse(LocalDate weekStart, List<CoverageCompanyDto> companies, int totalRequiredShifts, int totalAssignedShifts, int totalUnassignedShifts, double overallCoveragePct) {}
    public record ValidationProblem(String code, String message) {}
    public record EvaluationDto(UUID shiftOccurrenceId, String state, BigDecimal ic, List<ValidationProblem> blockers, List<String> warnings) {}
    public record DraftSaveResponse(UUID planId, Instant savedAt, String savedBy) {}
    public record CompatibilitySkillDto(String code,String label,BigDecimal actual,BigDecimal required,boolean meets) {}
    public record CompatibilityDetails(UUID assignmentId,UUID employeeId,String employeeName,UUID postId,String postCode,String postName,BigDecimal ic,int skillsMet,int skillsTotal,double compliancePct,List<CompatibilitySkillDto> skills) {}
    public record PostRequirementDto(String code,String label,BigDecimal required) {}
    public record ConsignmentMini(String code,String title,String instruction,String priority) {}
    public record PostDetails(UUID postId,String postCode,String postName,String tier,BigDecimal fhe,String clientName,String pointName,String city,List<PostRequirementDto> requirements,List<ConsignmentMini> consignments,List<Map<String,String>> recentNews) {}

    @POST
    @Path("/plans/ensure")
    @Transactional
    @Operation(summary="Materializa el plan semanal y sus Turnos Requeridos desde SIC: COM LOCAL")
    public PlanDto ensure(EnsurePlanRequest req) {
        if(req==null || req.companyId()==null) throw new BadRequestException("companyId obligatorio");
        scope.requireEditorForCompany(req.companyId());
        LocalDate week=normalizeWeek(req.weekStart());
        AssignmentPlanEntity plan=ensurePlan(req.companyId(),week);
        return planDto(plan);
    }

    @POST
    @Path("/plans/{planId}/save-draft")
    @Transactional
    public DraftSaveResponse saveDraft(@PathParam("planId") UUID planId) {
        AssignmentPlanEntity plan=plan(planId); scope.requireEditorForCompany(plan.companyId);
        if(!"DRAFT".equals(plan.status)) throw conflict("PLAN_NO_BORRADOR","Solo un plan en Borrador puede guardarse como borrador.");
        plan.draftSavedAt=Instant.now(); plan.draftSavedByUsername=identity.getPrincipal().getName();
        recordEvent(plan,null,null,"ASSIGNMENT_DRAFT_SAVED",null,null,plan.draftSavedByUsername,null,Map.of("savedAt",plan.draftSavedAt));
        return new DraftSaveResponse(plan.id,plan.draftSavedAt,plan.draftSavedByUsername);
    }

    @GET
    @Path("/week")
    @Transactional
    public WeekResponse week(@QueryParam("companyId") UUID companyId,
                             @QueryParam("weekStart") String weekStart,
                             @QueryParam("pointId") UUID pointId) {
        if(companyId==null) throw new BadRequestException("companyId obligatorio");
        scope.requireCompany(companyId);
        LocalDate week=normalizeWeek(parseDate(weekStart));
        AssignmentPlanEntity plan=ensurePlan(companyId,week);

        List<PointEntity> allPoints=PointEntity.list("instanceCountryId=?1 and companyId=?2 and status='ACTIVE' order by name",tenant.instanceCountryId(),companyId);
        List<PointEntity> visiblePoints=pointId==null?allPoints:allPoints.stream().filter(p->p.id.equals(pointId)).toList();
        Set<UUID> pointIds=visiblePoints.stream().map(p->p.id).collect(Collectors.toSet());
        List<PostEntity> allPosts=pointIds.isEmpty()?List.of():PostEntity.list("instanceCountryId=?1 and pointId in ?2 order by code",tenant.instanceCountryId(),pointIds);
        Set<UUID> postIds=allPosts.stream().map(p->p.id).collect(Collectors.toSet());
        Instant from=week.atStartOfDay(OPERATING_ZONE).toInstant();
        Instant to=week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();
        List<ShiftOccurrenceEntity> shifts=postIds.isEmpty()?List.of():ShiftOccurrenceEntity.list("instanceCountryId=?1 and postId in ?2 and startsAt>=?3 and startsAt<?4 order by startsAt,shiftName",tenant.instanceCountryId(),postIds,from,to);
        shifts=eligibleShiftsAfterServiceTransition(visiblePoints,allPosts,shifts);
        Set<UUID> shiftIds=shifts.stream().map(s->s.id).collect(Collectors.toSet());
        List<OperationalAssignmentEntity> assignments=shiftIds.isEmpty()?List.of():OperationalAssignmentEntity.list("instanceCountryId=?1 and assignmentPlanId=?2 and shiftOccurrenceId in ?3 and status<>'REMOVED'",tenant.instanceCountryId(),plan.id,shiftIds);

        Map<UUID,EmployeeOperationalSnapshot> employees=employeeMap(assignments.stream().map(OperationalAssignmentEntity::effectiveEmployeeId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<AssignmentDto> assignmentDtos=assignments.stream().map(a->toAssignmentDto(a,employees.get(a.effectiveEmployeeId()))).toList();
        int required=shifts.size();
        int assigned=assignments.size();
        int warnings=(int)assignmentDtos.stream().filter(a->!a.warnings().isEmpty()).count();
        double icAvg=assignmentDtos.stream().filter(a->a.ic()!=null).mapToDouble(a->a.ic().doubleValue()).average().orElse(0d);
        double coverage=required==0?100d:roundPct(100d*assigned/required);

        Map<UUID,PointEntity> pointMap=allPoints.stream().collect(Collectors.toMap(p->p.id,p->p));
        Map<UUID,PostPlanningCycleSnapshot> cycleByPost;
        if(postIds.isEmpty()) cycleByPost=Map.of(); else {
            List<PostPlanningCycleSnapshot> cycleSnapshots=PostPlanningCycleSnapshot.list("instanceCountryId=?1 and postId in ?2",tenant.instanceCountryId(),postIds);
            cycleByPost=cycleSnapshots.stream().collect(Collectors.toMap(x->x.postId,x->x));
        }
        return new WeekResponse(
            planDto(plan),
            allPoints.stream().map(p->new PointDto(p.id,p.code,p.name,p.clientName,p.city)).toList(),
            allPosts.stream().map(p->{
                PostPlanningCycleSnapshot c=cycleByPost.get(p.id);
                return new PostDto(p.id,p.pointId,p.code,p.name,p.format,p.fhe,p.tier,
                    c==null?null:c.rotationCode,c==null?null:c.cycleLengthDays,c==null?null:c.sourceVersion);
            }).toList(),
            shifts.stream().map(s->new ShiftDto(s.id,s.postId,s.templateId,s.localDate,s.shiftCode,s.shiftName,s.startsAt,s.endsAt,s.commercialVersion)).toList(),
            assignmentDtos,
            new SummaryDto(required,assigned,Math.max(required-assigned,0),coverage,warnings,roundPct(icAvg),
                plan.publishedRequiredShifts,plan.publishedAssignedShifts,plan.publishedCoveragePct==null?null:plan.publishedCoveragePct.doubleValue())
        );
    }

    @GET
    @Path("/personnel")
    @Transactional
    public PersonnelPage personnel(@QueryParam("companyId") UUID companyId,
                                   @QueryParam("weekStart") String weekStart,
                                   @QueryParam("q") String q,
                                   @QueryParam("role") String role,
                                   @QueryParam("availability") String availability,
                                   @QueryParam("page") @DefaultValue("0") int page,
                                   @QueryParam("size") @DefaultValue("50") int size) {
        if(companyId==null) throw new BadRequestException("companyId obligatorio");
        reconcileDueTransfers();
        scope.requireCompany(companyId);
        LocalDate week=normalizeWeek(parseDate(weekStart));
        int safePage=Math.max(page,0),safeSize=Math.min(Math.max(size,1),100);
        Instant from=week.atStartOfDay(OPERATING_ZONE).toInstant(),to=week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();
        List<String> openTransferStatuses=List.of("PENDING_ACCEPTANCE","ACCEPTED_PENDING_EFFECTIVE");

        List<EmployeeCompanyTransfer> incoming=EmployeeCompanyTransfer.list("instanceCountryId=?1 and destinationCompanyId=?2 and status in ?3 order by initiatedAt desc",tenant.instanceCountryId(),companyId,openTransferStatuses);
        Company viewingCompany=company(companyId);
        if(viewingCompany.alwaysActive||"COORDINATION".equals(viewingCompany.companyType)) incoming=incoming.stream().filter(t->scope.canManageKaibilTransferAgainst(t.originCompanyId)).toList();
        Set<UUID> incomingIds=incoming.stream().map(t->t.employeeId).collect(Collectors.toSet());

        StringBuilder hql=new StringBuilder("instanceCountryId=:tenant and employmentStatus='ACTIVE' and roleCode in :assignableRoles and ");
        Map<String,Object> params=new HashMap<>();params.put("tenant",tenant.instanceCountryId());params.put("companyId",companyId);
        params.put("assignableRoles",rolePolicy.assignableRoleCodes());
        if(incomingIds.isEmpty()) hql.append("companyId=:companyId"); else {hql.append("(companyId=:companyId or employeeId in :incomingIds)");params.put("incomingIds",incomingIds);}
        if(q!=null&&!q.isBlank()){hql.append(" and lower(fullName) like :q");params.put("q","%"+q.trim().toLowerCase(Locale.ROOT)+"%");}
        if(role!=null&&!role.isBlank()){
            if(!rolePolicy.isAssignable(role)) throw new BadRequestException("El cargo seleccionado no participa en Asignaciones.");
            hql.append(" and roleCode=:role");params.put("role",role);
        }
        String availabilityKey=availability==null?"":availability.trim().toUpperCase(Locale.ROOT);
        if("REQUIRED_CHANGE".equals(availabilityKey))hql.append(" and requiredChange=true");
        else if("MEDICAL_LEAVE".equals(availabilityKey)||"VACATION".equals(availabilityKey)||"PERMISSION".equals(availabilityKey)){
            List<EmployeeUnavailabilitySnapshot> matching=EmployeeUnavailabilitySnapshot.list("instanceCountryId=?1 and type=?2 and sourceStatus='ACTIVE' and startsAt<?3 and endsAt>?4",tenant.instanceCountryId(),availabilityKey,to,from);
            Set<UUID> ids=matching.stream().map(u->u.employeeId).collect(Collectors.toSet());
            if(ids.isEmpty())return new PersonnelPage(List.of(),0,safePage,safeSize);
            hql.append(" and employeeId in :availabilityIds");params.put("availabilityIds",ids);
        }else if("INCOMING".equals(availabilityKey)){
            if(incomingIds.isEmpty())return new PersonnelPage(List.of(),0,safePage,safeSize);
            hql.append(" and employeeId in :availabilityIds");params.put("availabilityIds",incomingIds);
        }else if("OUTGOING".equals(availabilityKey)){
            Set<UUID> ids=EmployeeCompanyTransfer.<EmployeeCompanyTransfer>list("instanceCountryId=?1 and originCompanyId=?2 and status in ?3",tenant.instanceCountryId(),companyId,openTransferStatuses).stream().map(t->t.employeeId).collect(Collectors.toSet());
            if(ids.isEmpty())return new PersonnelPage(List.of(),0,safePage,safeSize);
            hql.append(" and employeeId in :availabilityIds");params.put("availabilityIds",ids);
        }else if("AVAILABLE".equals(availabilityKey)){
            Set<UUID> blocked=EmployeeUnavailabilitySnapshot.<EmployeeUnavailabilitySnapshot>list("instanceCountryId=?1 and sourceStatus='ACTIVE' and startsAt<?2 and endsAt>?3",tenant.instanceCountryId(),to,from).stream().map(u->u.employeeId).collect(Collectors.toSet());
            EmployeeCompanyTransfer.<EmployeeCompanyTransfer>list("instanceCountryId=?1 and status in ?2 and (originCompanyId=?3 or destinationCompanyId=?3)",tenant.instanceCountryId(),openTransferStatuses,companyId).stream().map(t->t.employeeId).forEach(blocked::add);
            if(!blocked.isEmpty()){hql.append(" and employeeId not in :blockedIds");params.put("blockedIds",blocked);}
        }
        String where=hql.toString();
        long total=EmployeeOperationalSnapshot.count(where,params);
        List<EmployeeOperationalSnapshot> pageRows=EmployeeOperationalSnapshot.<EmployeeOperationalSnapshot>find(where+" order by fullName",params).page(Page.of(safePage,safeSize)).list();
        if(pageRows.isEmpty())return new PersonnelPage(List.of(),total,safePage,safeSize);

        Set<UUID> employeeIds=pageRows.stream().map(e->e.employeeId).collect(Collectors.toSet());
        List<EmployeeUnavailabilitySnapshot> unavs=EmployeeUnavailabilitySnapshot.list("instanceCountryId=?1 and employeeId in ?2 and startsAt<?3 and endsAt>?4 and sourceStatus='ACTIVE' order by startsAt",tenant.instanceCountryId(),employeeIds,to,from);
        Map<UUID,List<EmployeeUnavailabilitySnapshot>> unavByEmployee=unavs.stream().collect(Collectors.groupingBy(u->u.employeeId));
        AssignmentPlanEntity plan=AssignmentPlanEntity.find("instanceCountryId=?1 and companyId=?2 and weekStart=?3",tenant.instanceCountryId(),companyId,week).firstResult();
        Map<UUID,Double> assignedHours=plan==null?Map.of():assignedHoursByEmployee(plan.id,from,to,employeeIds);
        Map<UUID,EmployeeCompanyTransfer> openByEmployee=new HashMap<>();
        List<EmployeeCompanyTransfer> open=EmployeeCompanyTransfer.list("instanceCountryId=?1 and employeeId in ?2 and status in ?3",tenant.instanceCountryId(),employeeIds,openTransferStatuses);
        for(EmployeeCompanyTransfer t:open)openByEmployee.put(t.employeeId,t);
        Set<UUID> transferCompanyIds=open.stream().flatMap(t->java.util.stream.Stream.of(t.originCompanyId,t.destinationCompanyId)).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID,Company> transferCompanies=companyMap(transferCompanyIds);
        List<PersonnelDto> items=pageRows.stream().map(e->{EmployeeCompanyTransfer t=openByEmployee.get(e.employeeId);TransferMiniDto mini=t==null?null:transferMini(t,Objects.equals(t.destinationCompanyId,companyId)?"INCOMING":"OUTGOING",transferCompanies);return new PersonnelDto(e.employeeId,e.fullName,e.roleCode,e.employmentStatus,e.idScore,e.preferredShift,e.photoKey,e.requiredChange,roundOne(assignedHours.getOrDefault(e.employeeId,0d)),unavByEmployee.getOrDefault(e.employeeId,List.of()).stream().map(this::unavailabilityDto).toList(),mini);}).toList();
        return new PersonnelPage(items,total,safePage,safeSize);
    }

    @GET
    @Path("/personnel-roles")
    @Transactional
    public List<PersonnelRoleDto> personnelRoles(@QueryParam("companyId") UUID companyId) {
        if(companyId==null) throw new BadRequestException("companyId obligatorio");
        scope.requireCompany(companyId);
        List<EmployeeOperationalSnapshot> rows=EmployeeOperationalSnapshot.list(
            "instanceCountryId=?1 and companyId=?2 and employmentStatus='ACTIVE' and roleCode in ?3 order by roleCode",
            tenant.instanceCountryId(),companyId,rolePolicy.assignableRoleCodes());
        return rows.stream().map(e->e.roleCode).filter(Objects::nonNull).distinct()
            .map(code->new PersonnelRoleDto(code,roleLabel(code))).toList();
    }

    @GET
    @Path("/employees/{employeeId}")
    @Transactional
    public EmployeeDetails employee(@PathParam("employeeId") UUID employeeId,@QueryParam("weekStart") String weekStart,@QueryParam("companyId") UUID viewingCompanyId) {
        reconcileDueTransfers();
        EmployeeOperationalSnapshot e=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();if(e==null)throw new NotFoundException();
        EmployeeCompanyTransfer transfer=openTransfer(employeeId);boolean visible=scope.allowedCompanyIds().contains(e.companyId)||(transfer!=null&&scope.allowedCompanyIds().contains(transfer.destinationCompanyId));if(!visible)throw new ForbiddenException("Colaborador fuera del alcance del usuario.");
        EmployeeSkillSnapshot skills=EmployeeSkillSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();List<SkillDto> skillDtos=skills==null?List.of():skillDtos(skills);
        List<EmployeeUnavailabilitySnapshot> unavs=EmployeeUnavailabilitySnapshot.list("instanceCountryId=?1 and employeeId=?2 order by startsAt desc",tenant.instanceCountryId(),employeeId);
        List<OperationalAssignmentEntity> recent=OperationalAssignmentEntity.find("instanceCountryId=?1 and (employeeId=?2 or actualEmployeeId=?2) and status<>'REMOVED' order by updatedAt desc",tenant.instanceCountryId(),employeeId).page(Page.of(0,12)).list();
        Map<UUID,ShiftOccurrenceEntity> shifts=shiftMap(recent.stream().map(a->a.shiftOccurrenceId).collect(Collectors.toSet()));Set<UUID> postIds=shifts.values().stream().map(s->s.postId).collect(Collectors.toSet());Map<UUID,PostEntity> posts=postIds.isEmpty()?Map.of():PostEntity.<PostEntity>list("instanceCountryId=?1 and id in ?2",tenant.instanceCountryId(),postIds).stream().collect(Collectors.toMap(x->x.id,x->x));
        List<AssignmentAuditDto> audits=recent.stream().map(a->{ShiftOccurrenceEntity sh=shifts.get(a.shiftOccurrenceId);PostEntity post=sh==null?null:posts.get(sh.postId);return new AssignmentAuditDto(post==null?"—":post.code,post==null?"—":post.name,sh==null?"—":sh.shiftName,sh==null?null:sh.startsAt,a.status,a.actualAssignedByUsername!=null?a.actualAssignedByUsername:a.assignedByUsername);}).toList();
        Company company=Company.find("id=?1 and instanceCountryId=?2",e.companyId,tenant.instanceCountryId()).firstResult();String direction=transfer==null?null:(viewingCompanyId!=null&&Objects.equals(transfer.destinationCompanyId,viewingCompanyId)?"INCOMING":"OUTGOING");
        return new EmployeeDetails(e.employeeId,e.fullName,e.roleCode,e.employmentStatus,e.idScore,e.preferredShift,e.photoKey,e.requiredChange,e.companyId,company==null?"—":company.name,skillDtos,unavs.stream().map(this::unavailabilityDto).toList(),audits,transfer==null?null:transferMini(transfer,direction));
    }

    @GET @Path("/transfer-reasons")
    public List<TransferReasonDto> transferReasons(){return TransferReasonCatalog.<TransferReasonCatalog>list("instanceCountryId=?1 and active=true order by sortOrder",tenant.instanceCountryId()).stream().map(r->new TransferReasonDto(r.code,r.label)).toList();}

    @GET @Path("/transfer-destinations")
    public List<TransferDestinationDto> transferDestinations(@QueryParam("originCompanyId") UUID originCompanyId){
        if(originCompanyId==null)throw new BadRequestException("originCompanyId obligatorio");scope.requireEditorForCompany(originCompanyId);Company origin=company(originCompanyId);
        return Company.<Company>list("instanceCountryId=?1 and status='ACTIVE' and id<>?2 order by name",tenant.instanceCountryId(),originCompanyId).stream()
                .filter(c->!(origin.alwaysActive||"COORDINATION".equals(origin.companyType))||scope.canManageKaibilTransferAgainst(c.id))
                .map(c->new TransferDestinationDto(c.id,c.code,c.name,c.companyType,c.alwaysActive)).toList();
    }

    @POST @Path("/transfers")
    @Transactional
    public CompanyTransferDto createTransfer(TransferRequest req){
        if(req==null||req.employeeId()==null||req.destinationCompanyId()==null||req.reasonCode()==null||req.reasonCode().isBlank())throw new BadRequestException("Colaborador, Compañía destino y Motivo son obligatorios.");
        if(req.observations()==null||req.observations().isBlank())throw new BadRequestException("Observaciones obligatorias.");if(req.observations().trim().length()>500)throw new BadRequestException("Observaciones: máximo 500 caracteres.");
        EmployeeOperationalSnapshot employee=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),req.employeeId()).firstResult();if(employee==null)throw new NotFoundException("Colaborador no encontrado");
        UUID originId=employee.companyId;scope.requireEditorForCompany(originId);if(Objects.equals(originId,req.destinationCompanyId()))throw new BadRequestException("La Compañía destino debe ser diferente a la actual.");
        Company destination=Company.find("id=?1 and instanceCountryId=?2 and status='ACTIVE'",req.destinationCompanyId(),tenant.instanceCountryId()).firstResult();if(destination==null)throw new BadRequestException("Compañía destino no disponible.");
        Company origin=company(originId);if((origin.alwaysActive||"COORDINATION".equals(origin.companyType))&&!scope.canManageKaibilTransferAgainst(destination.id))throw new ForbiddenException("La Compañía destino está fuera del ámbito territorial del usuario para movimientos desde Kaibil.");
        if(openTransfer(employee.employeeId)!=null)throw conflict("TRANSFERENCIA_ABIERTA","El colaborador ya tiene una transferencia pendiente.");
        TransferReasonCatalog reason=TransferReasonCatalog.find("instanceCountryId=?1 and code=?2 and active=true",tenant.instanceCountryId(),req.reasonCode()).firstResult();if(reason==null)throw new BadRequestException("Motivo inválido.");
        EmployeeCompanyTransfer t=new EmployeeCompanyTransfer();t.instanceCountryId=tenant.instanceCountryId();t.employeeId=employee.employeeId;t.originCompanyId=originId;t.destinationCompanyId=destination.id;t.reasonCode=reason.code;t.reasonLabelSnapshot=reason.label;t.observations=req.observations().trim();t.status="PENDING_ACCEPTANCE";t.initiatedByUsername=identity.getPrincipal().getName();t.initiatedAt=Instant.now();t.releasedFutureAssignments=0;t.rrhhSyncStatus="PENDING";t.persist();
        t.releasedFutureAssignments=releaseFutureAssignments(employee.employeeId,originId,t.id);OperationalAssignmentEntity current=currentAssignment(employee.employeeId,originId,Instant.now());if(current!=null)t.currentShiftAssignmentId=current.id;
        OutboxEvent.of(tenant.instanceCountryId(),"EMPLOYEE_COMPANY_TRANSFER",t.id,"TRANSFER_REQUESTED",json(Map.of("employeeId",employee.employeeId,"originCompanyId",originId,"destinationCompanyId",destination.id,"reasonCode",reason.code))).persist();
        return transferDto(t,employee);
    }

    @POST @Path("/transfers/{transferId}/accept")
    @Transactional
    public CompanyTransferDto acceptTransfer(@PathParam("transferId") UUID transferId,TransferDecisionRequest req){EmployeeCompanyTransfer t=transfer(transferId);requireDestinationDecision(t);if(!"PENDING_ACCEPTANCE".equals(t.status))throw conflict("TRANSFERENCIA_CERRADA","La transferencia ya no está pendiente de aceptación.");Instant now=Instant.now();OperationalAssignmentEntity current=currentAssignment(t.employeeId,t.originCompanyId,now);t.decisionByUsername=identity.getPrincipal().getName();t.decisionAt=now;t.decisionNote=req==null?null:blankToNull(req.note());if(current!=null){ShiftOccurrenceEntity sh=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",current.shiftOccurrenceId,tenant.instanceCountryId()).firstResult();t.status="ACCEPTED_PENDING_EFFECTIVE";t.effectiveAt=sh==null?now:sh.endsAt;t.currentShiftAssignmentId=current.id;t.rrhhSyncStatus="PENDING_EFFECTIVE";}else completeTransfer(t,now);OutboxEvent.of(tenant.instanceCountryId(),"EMPLOYEE_COMPANY_TRANSFER",t.id,"TRANSFER_ACCEPTED",json(Map.of("employeeId",t.employeeId,"effectiveAt",t.effectiveAt))).persist();return transferDto(t,employeeSnapshot(t.employeeId));}

    @POST @Path("/transfers/{transferId}/reject")
    @Transactional
    public CompanyTransferDto rejectTransfer(@PathParam("transferId") UUID transferId,TransferDecisionRequest req){EmployeeCompanyTransfer t=transfer(transferId);requireDestinationDecision(t);if(!"PENDING_ACCEPTANCE".equals(t.status))throw conflict("TRANSFERENCIA_CERRADA","La transferencia ya no está pendiente de aceptación.");t.status="REJECTED";t.decisionByUsername=identity.getPrincipal().getName();t.decisionAt=Instant.now();t.decisionNote=req==null?null:blankToNull(req.note());t.rrhhSyncStatus="NOT_REQUIRED";OutboxEvent.of(tenant.instanceCountryId(),"EMPLOYEE_COMPANY_TRANSFER",t.id,"TRANSFER_REJECTED",json(Map.of("employeeId",t.employeeId))).persist();return transferDto(t,employeeSnapshot(t.employeeId));}

    @POST @Path("/transfers/{transferId}/cancel")
    @Transactional
    public CompanyTransferDto cancelTransfer(@PathParam("transferId") UUID transferId,TransferDecisionRequest req){EmployeeCompanyTransfer t=transfer(transferId);Company origin=company(t.originCompanyId);scope.requireEditorForCompany(t.originCompanyId);if((origin.alwaysActive||"COORDINATION".equals(origin.companyType))&&!scope.canManageKaibilTransferAgainst(t.destinationCompanyId))throw new ForbiddenException("La transferencia está fuera del ámbito territorial del usuario.");if(!"PENDING_ACCEPTANCE".equals(t.status))throw conflict("TRANSFERENCIA_CERRADA","Solo una transferencia pendiente puede anularse.");t.status="CANCELLED";t.decisionByUsername=identity.getPrincipal().getName();t.decisionAt=Instant.now();t.decisionNote=req==null?null:blankToNull(req.note());t.rrhhSyncStatus="NOT_REQUIRED";OutboxEvent.of(tenant.instanceCountryId(),"EMPLOYEE_COMPANY_TRANSFER",t.id,"TRANSFER_CANCELLED",json(Map.of("employeeId",t.employeeId))).persist();return transferDto(t,employeeSnapshot(t.employeeId));}

    @GET @Path("/transfers/employee/{employeeId}")
    public List<CompanyTransferDto> transferHistory(@PathParam("employeeId") UUID employeeId){EmployeeOperationalSnapshot e=employeeSnapshot(employeeId);if(!scope.allowedCompanyIds().contains(e.companyId)){List<EmployeeCompanyTransfer> ts=EmployeeCompanyTransfer.list("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId);boolean visible=ts.stream().anyMatch(t->scope.allowedCompanyIds().contains(t.originCompanyId)||scope.allowedCompanyIds().contains(t.destinationCompanyId));if(!visible)throw new ForbiddenException();}return EmployeeCompanyTransfer.<EmployeeCompanyTransfer>list("instanceCountryId=?1 and employeeId=?2 order by initiatedAt desc",tenant.instanceCountryId(),employeeId).stream().map(t->transferDto(t,e)).toList();}

    @GET
    @Path("/compatibility/{assignmentId}")
    @Transactional
    public CompatibilityDetails compatibilityDetails(@PathParam("assignmentId") UUID assignmentId) {
        OperationalAssignmentEntity a=OperationalAssignmentEntity.find("id=?1 and instanceCountryId=?2",assignmentId,tenant.instanceCountryId()).firstResult();
        if(a==null) throw new NotFoundException("Asignación no encontrada");
        AssignmentPlanEntity plan=plan(a.assignmentPlanId); scope.requireCompany(plan.companyId);
        ShiftOccurrenceEntity shift=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",a.shiftOccurrenceId,tenant.instanceCountryId()).firstResult();
        if(shift==null) throw new NotFoundException("Turno no encontrado");
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",shift.postId,tenant.instanceCountryId()).firstResult();
        if(post==null) throw new NotFoundException("Puesto no encontrado");
        UUID employeeId=a.effectiveEmployeeId(); EmployeeOperationalSnapshot employee=employeeId==null?null:EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        EmployeeSkillSnapshot actual=employeeId==null?null:EmployeeSkillSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        PostSkillRequirement required=PostSkillRequirement.find("instanceCountryId=?1 and postId=?2",tenant.instanceCountryId(),post.id).firstResult();
        List<CompatibilitySkillDto> skills=compatibilitySkills(actual,required); int met=(int)skills.stream().filter(CompatibilitySkillDto::meets).count(); double compliance=skills.isEmpty()?0d:roundPct(100d*met/skills.size());
        return new CompatibilityDetails(a.id,employeeId,employee==null?"—":employee.fullName,post.id,post.code,post.name,a.compatibilityIndex,met,skills.size(),compliance,skills);
    }

    @GET
    @Path("/posts/{postId}/details")
    @Transactional
    public PostDetails postDetails(@PathParam("postId") UUID postId) {
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",postId,tenant.instanceCountryId()).firstResult(); if(post==null) throw new NotFoundException("Puesto no encontrado");
        PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",post.pointId,tenant.instanceCountryId()).firstResult(); if(point==null) throw new NotFoundException("Punto no encontrado"); scope.requireCompany(point.companyId);
        PostSkillRequirement req=PostSkillRequirement.find("instanceCountryId=?1 and postId=?2",tenant.instanceCountryId(),post.id).firstResult();
        List<PostRequirementDto> requirements=postRequirementDtos(req);
        List<Consignment> candidates=Consignment.list("instanceCountryId=?1 and pointId=?2 and status='VIGENTE' order by priority desc,code",tenant.instanceCountryId(),point.id);
        List<Consignment> cs=candidates.stream().filter(c->"POINT".equals(c.scopeType)||ConsignmentPostScope.count("instanceCountryId=?1 and consignmentId=?2 and postId=?3",tenant.instanceCountryId(),c.id,post.id)>0).toList();
        List<ConsignmentMini> consignments=cs.stream().map(c->new ConsignmentMini(c.code,c.title,c.instruction,c.priority)).toList();
        return new PostDetails(post.id,post.code,post.name,post.tier,post.fhe,point.clientName,point.name,point.city,requirements,consignments,List.of());
    }

    @GET
    @Path("/evaluate")
    @Transactional
    public List<EvaluationDto> evaluate(@QueryParam("planId") UUID planId, @QueryParam("employeeId") UUID employeeId) {
        if(planId==null||employeeId==null) throw new BadRequestException("planId y employeeId obligatorios");
        AssignmentPlanEntity plan=plan(planId); scope.requireCompany(plan.companyId);
        EmployeeOperationalSnapshot employee=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        if(employee==null) throw new NotFoundException("Colaborador no encontrado");
        List<ShiftOccurrenceEntity> shifts=planShifts(plan);if(shifts.isEmpty())return List.of();
        Set<UUID> postIds=shifts.stream().map(x->x.postId).collect(Collectors.toSet());
        Map<UUID,PostEntity> posts=PostEntity.<PostEntity>list("instanceCountryId=?1 and id in ?2",tenant.instanceCountryId(),postIds).stream().collect(Collectors.toMap(x->x.id,x->x));
        Map<UUID,PostSkillRequirement> requirements=PostSkillRequirement.<PostSkillRequirement>list("instanceCountryId=?1 and postId in ?2",tenant.instanceCountryId(),postIds).stream().collect(Collectors.toMap(x->x.postId,x->x));
        EmployeeSkillSnapshot employeeSkills=EmployeeSkillSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        boolean transferPending=openTransfer(employeeId)!=null;
        Instant minStart=shifts.stream().map(x->x.startsAt).min(Comparator.naturalOrder()).orElse(Instant.now());
        Instant maxEnd=shifts.stream().map(x->x.endsAt).max(Comparator.naturalOrder()).orElse(minStart);
        List<EmployeeUnavailabilitySnapshot> unavailability=EmployeeUnavailabilitySnapshot.list("instanceCountryId=?1 and employeeId=?2 and sourceStatus='ACTIVE' and startsAt<?3 and endsAt>?4 order by startsAt",tenant.instanceCountryId(),employeeId,maxEnd,minStart);
        // Evaluation only needs assignments that can overlap or be immediately adjacent to
        // the plan window. Do not load the employee's complete assignment history.
        List<ShiftOccurrenceEntity> nearbyShifts=ShiftOccurrenceEntity.list("instanceCountryId=?1 and startsAt<=?2 and endsAt>=?3",tenant.instanceCountryId(),maxEnd,minStart);
        Set<UUID> nearbyShiftIds=nearbyShifts.stream().map(s->s.id).collect(Collectors.toSet());
        Set<UUID> assignedShiftIds=nearbyShiftIds.isEmpty()?Set.of():OperationalAssignmentEntity.<OperationalAssignmentEntity>list("instanceCountryId=?1 and status<>'REMOVED' and shiftOccurrenceId in ?2 and (employeeId=?3 or actualEmployeeId=?3)",tenant.instanceCountryId(),nearbyShiftIds,employeeId).stream().map(a->a.shiftOccurrenceId).collect(Collectors.toSet());
        List<ShiftOccurrenceEntity> assignedShifts=assignedShiftIds.isEmpty()?List.of():nearbyShifts.stream().filter(s->assignedShiftIds.contains(s.id)).toList();
        boolean companyMismatch=!Objects.equals(employee.companyId,plan.companyId),inactive=!"ACTIVE".equals(employee.employmentStatus);
        boolean unassignableRole=!rolePolicy.isAssignable(employee.roleCode);

        List<EvaluationDto> out=new ArrayList<>(shifts.size());
        for(ShiftOccurrenceEntity shift:shifts){
            PostEntity post=posts.get(shift.postId);PostSkillRequirement req=requirements.get(shift.postId);
            List<ValidationProblem> hard=new ArrayList<>();
            if(companyMismatch)hard.add(new ValidationProblem("COMPANY","El colaborador no pertenece a la Compañía del plan."));
            if(inactive)hard.add(new ValidationProblem("EMPLOYMENT_STATUS","El colaborador no está activo en SIC: RRHH."));
            if(unassignableRole)hard.add(new ValidationProblem("ROLE_NOT_ASSIGNABLE","El cargo del colaborador no está habilitado para Asignaciones."));
            if(transferPending)hard.add(new ValidationProblem("TRANSFER_PENDING","El colaborador tiene una transferencia de Compañía pendiente y no admite nuevas asignaciones futuras."));
            if(unavailability.stream().anyMatch(u->u.startsAt.isBefore(shift.endsAt)&&u.endsAt.isAfter(shift.startsAt)))hard.add(new ValidationProblem("UNAVAILABLE","SIC: RRHH reporta vacaciones, permiso médico u otra indisponibilidad para este intervalo."));
            for(ShiftOccurrenceEntity other:assignedShifts){
                if(other.id.equals(shift.id))continue;
                boolean overlap=other.startsAt.isBefore(shift.endsAt)&&other.endsAt.isAfter(shift.startsAt);
                if(overlap){hard.add(new ValidationProblem("OVERLAP","Existe otra asignación que se solapa con este turno."));break;}
                boolean consecutive=other.endsAt.equals(shift.startsAt)||shift.endsAt.equals(other.startsAt);
                if(consecutive){hard.add(new ValidationProblem("AUTO_RELEVO","La asignación produciría auto-relevo/turnos inmediatamente consecutivos."));break;}
            }
            List<String> soft=post==null?new ArrayList<>():warnings(post,employee,req);
            BigDecimal ic=compatibility(employeeSkills,req);
            if(ic!=null&&ic.compareTo(BigDecimal.valueOf(100))<0)soft.add("IC "+ic.stripTrailingZeros().toPlainString()+"%: compatibilidad inferior a 100% (alerta, no bloqueo).");
            boolean icCompliant=ic!=null&&ic.compareTo(BigDecimal.valueOf(100))>=0;
            boolean idCompliant=post!=null&&employee.idScore!=null&&employee.idScore.doubleValue()>=tierMinimum(post.tier);
            String state=!hard.isEmpty()?"RED":(icCompliant&&idCompliant?"GREEN":"AMBER");
            out.add(new EvaluationDto(shift.id,state,ic,hard,soft));
        }
        return out;
    }

    @POST
    @Path("/assign")
    @Transactional
    public Response assign(AssignRequest req) {
        if(req==null||req.planId()==null||req.shiftOccurrenceId()==null||req.employeeId()==null) throw new BadRequestException("planId, shiftOccurrenceId y employeeId obligatorios");
        AssignmentPlanEntity plan=plan(req.planId());
        scope.requireEditorForCompany(plan.companyId);
        if("CLOSED".equals(plan.status)) throw conflict("PLAN_CERRADO","El plan está cerrado y no admite cambios.");
        ShiftOccurrenceEntity shift=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",req.shiftOccurrenceId(),tenant.instanceCountryId()).firstResult();
        if(shift==null) throw new NotFoundException("Turno requerido no encontrado");
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",shift.postId,tenant.instanceCountryId()).firstResult();
        if(post==null) throw new NotFoundException("Puesto no encontrado");
        PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",post.pointId,tenant.instanceCountryId()).firstResult();
        if(point==null||!Objects.equals(point.companyId,plan.companyId)) throw conflict("COMPANIA_INVALIDA","El turno no pertenece a la Compañía del plan.");
        if(point.operationalTransitionUntil!=null&&shift.startsAt.isBefore(point.operationalTransitionUntil)) throw conflict("SERVICIO_EN_TRANSICION","El Servicio conserva un turno de la Compañía anterior hasta "+point.operationalTransitionUntil+". No puede asignarse personal nuevo antes de ese momento.");
        EmployeeOperationalSnapshot employee=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),req.employeeId()).firstResult();
        if(employee==null) throw new NotFoundException("Colaborador no encontrado");

        List<ValidationProblem> blockers=blockers(plan,shift,employee);
        if(!blockers.isEmpty()) return Response.status(Response.Status.CONFLICT).entity(Map.of("blocking",true,"problems",blockers)).build();
        List<String> warnings=warnings(post,employee);
        BigDecimal ic=compatibility(post.id,employee.employeeId);
        if(ic!=null && ic.compareTo(BigDecimal.valueOf(100))<0) warnings.add("IC " + ic.stripTrailingZeros().toPlainString() + "%: compatibilidad inferior a 100% (alerta, no bloqueo).");

        OperationalAssignmentEntity existing=OperationalAssignmentEntity.find("instanceCountryId=?1 and assignmentPlanId=?2 and shiftOccurrenceId=?3 and status<>'REMOVED'",tenant.instanceCountryId(),plan.id,shift.id).firstResult();
        Instant now=Instant.now(); String actor=identity.getPrincipal().getName();
        String eventType;
        if("DRAFT".equals(plan.status)) {
            if(existing==null){
                existing=new OperationalAssignmentEntity(); existing.instanceCountryId=tenant.instanceCountryId(); existing.assignmentPlanId=plan.id; existing.shiftOccurrenceId=shift.id;
                existing.employeeId=employee.employeeId; existing.status="DRAFT"; existing.assignedByUsername=actor; existing.assignedAt=now; eventType=req.sourceAssignmentId()!=null?"ASSIGNMENT_COPIED":"ASSIGNMENT_CREATED";
                existing.persist();
            } else {
                UUID before=existing.employeeId;
                existing.employeeId=employee.employeeId; existing.actualEmployeeId=null; existing.status="DRAFT"; existing.assignedByUsername=actor; existing.assignedAt=now; existing.actualAssignedByUsername=null; existing.actualAssignedAt=null; existing.reassignmentReason=null;
                eventType=Objects.equals(before,employee.employeeId)?"ASSIGNMENT_CONFIRMED":"ASSIGNMENT_MOVED";
            }
        } else {
            // El snapshot publicado es inmutable. Los cambios posteriores son eventos explícitos.
            if(existing==null){
                existing=new OperationalAssignmentEntity(); existing.instanceCountryId=tenant.instanceCountryId(); existing.assignmentPlanId=plan.id; existing.shiftOccurrenceId=shift.id; existing.employeeId=null; existing.status="POST_PUBLISH_ASSIGNED";
                existing.actualEmployeeId=employee.employeeId; existing.actualAssignedByUsername=actor; existing.actualAssignedAt=now; existing.reassignmentReason=blankToDefault(req.reason(),"Asignación posterior a publicación"); existing.persist();
                eventType="POST_PUBLICATION_ASSIGNMENT";
            } else {
                UUID before=existing.effectiveEmployeeId();
                existing.actualEmployeeId=employee.employeeId; existing.status="REASSIGNED"; existing.actualAssignedByUsername=actor; existing.actualAssignedAt=now; existing.reassignmentReason=blankToDefault(req.reason(),"Reasignación posterior a publicación");
                eventType="REASSIGNMENT_CREATED";
                recordEvent(plan,existing,shift,eventType,before,employee.employeeId,actor,existing.reassignmentReason,assignmentPayload(warnings,ic,employee.idScore));
            }
        }
        existing.compatibilityIndex=ic; existing.idScore=employee.idScore; existing.warningJson=json(warnings);
        if(!"REASSIGNMENT_CREATED".equals(eventType))
            recordEvent(plan,existing,shift,eventType,existing.employeeId,employee.employeeId,actor,req.reason(),assignmentPayload(warnings,ic,employee.idScore));
        return Response.ok(toAssignmentDto(existing,employee)).build();
    }

    @DELETE
    @Path("/{assignmentId}")
    @Transactional
    public Response remove(@PathParam("assignmentId") UUID assignmentId) {
        OperationalAssignmentEntity a=OperationalAssignmentEntity.find("id=?1 and instanceCountryId=?2",assignmentId,tenant.instanceCountryId()).firstResult();
        if(a==null) throw new NotFoundException();
        AssignmentPlanEntity plan=plan(a.assignmentPlanId);
        scope.requireEditorForCompany(plan.companyId);
        if(!"DRAFT".equals(plan.status)) throw conflict("PLAN_PUBLICADO","Un plan publicado no se edita silenciosamente; utilice Reasignación.");
        a.status="REMOVED";
        recordEvent(plan,a,null,"ASSIGNMENT_REMOVED",a.employeeId,null,identity.getPrincipal().getName(),null,Map.of());
        return Response.noContent().build();
    }

    @POST
    @Path("/plans/{planId}/publish")
    @Transactional
    public PublishResponse publish(@PathParam("planId") UUID planId) {
        AssignmentPlanEntity plan=plan(planId);
        scope.requireEditorForCompany(plan.companyId);
        if("CLOSED".equals(plan.status)) throw conflict("PLAN_CERRADO","El plan ya está cerrado.");
        if("PUBLISHED".equals(plan.status)) return publishResponse(plan);
        List<ShiftOccurrenceEntity> shifts=planShifts(plan);
        List<OperationalAssignmentEntity> assignments=OperationalAssignmentEntity.list("instanceCountryId=?1 and assignmentPlanId=?2 and status<>'REMOVED'",tenant.instanceCountryId(),plan.id);
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("planId",plan.id); snapshot.put("companyId",plan.companyId); snapshot.put("weekStart",plan.weekStart);
        snapshot.put("publishedAt",Instant.now());
        snapshot.put("shifts",shifts.stream().map(s->Map.of("shiftOccurrenceId",s.id,"postId",s.postId,"startsAt",s.startsAt,"endsAt",s.endsAt,"shiftCode",s.shiftCode,"commercialVersion",s.commercialVersion)).toList());
        snapshot.put("assignments",assignments.stream().map(this::publicationAssignmentSnapshot).toList());
        int requiredAtPublish=shifts.size();
        int assignedAtPublish=assignments.size();
        double coverageAtPublish=requiredAtPublish==0?100d:roundPct(100d*assignedAtPublish/requiredAtPublish);
        snapshot.put("publishedCoverage",Map.of("requiredShifts",requiredAtPublish,"assignedShifts",assignedAtPublish,"unassignedShifts",Math.max(requiredAtPublish-assignedAtPublish,0),"coveragePct",coverageAtPublish));
        plan.status="PUBLISHED"; plan.publishedAt=Instant.now(); plan.publishedByUsername=identity.getPrincipal().getName(); plan.publishedSnapshotJson=json(snapshot);
        plan.publishedRequiredShifts=requiredAtPublish; plan.publishedAssignedShifts=assignedAtPublish; plan.publishedCoveragePct=BigDecimal.valueOf(coverageAtPublish).setScale(2,RoundingMode.HALF_UP);
        assignments.forEach(a->{ if("DRAFT".equals(a.status)) a.status="PUBLISHED"; });
        recordEvent(plan,null,null,"ASSIGNMENT_PLAN_PUBLISHED",null,null,plan.publishedByUsername,null,Map.of("requiredShifts",shifts.size(),"assignedShifts",assignments.size()));
        OutboxEvent.of(tenant.instanceCountryId(),"ASSIGNMENT_PLAN",plan.id,"ASSIGNMENT_PLAN_PUBLISHED",json(Map.of("planId",plan.id,"companyId",plan.companyId,"weekStart",plan.weekStart,"publishedAt",plan.publishedAt))).persist();
        return publishResponse(plan);
    }

    @GET
    @Path("/coverage")
    @Transactional
    public CoverageResponse coverage(@QueryParam("weekStart") String weekStart) {
        LocalDate week=normalizeWeek(parseDate(weekStart));
        Set<UUID> allowedCompanies=scope.allowedCompanyIds();
        List<Company> companies=allowedCompanies.isEmpty()?List.of():Company.list("instanceCountryId=?1 and status='ACTIVE' and id in ?2 order by name",tenant.instanceCountryId(),allowedCompanies);
        List<CoverageCompanyDto> result=new ArrayList<>();
        int totalReq=0,totalAssigned=0;
        for(Company c:companies){
            AssignmentPlanEntity plan=ensurePlan(c.id,week);
            List<PointEntity> points=PointEntity.list("instanceCountryId=?1 and companyId=?2 and status='ACTIVE'",tenant.instanceCountryId(),c.id);
            Set<UUID> pids=points.stream().map(p->p.id).collect(Collectors.toSet());
            List<PostEntity> posts=pids.isEmpty()?List.of():PostEntity.list("instanceCountryId=?1 and pointId in ?2",tenant.instanceCountryId(),pids);
            Set<UUID> postIds=posts.stream().map(p->p.id).collect(Collectors.toSet());
            Instant from=week.atStartOfDay(OPERATING_ZONE).toInstant(), to=week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();
            List<ShiftOccurrenceEntity> shifts=postIds.isEmpty()?List.of():ShiftOccurrenceEntity.list("instanceCountryId=?1 and postId in ?2 and startsAt>=?3 and startsAt<?4",tenant.instanceCountryId(),postIds,from,to);
            shifts=eligibleShiftsAfterServiceTransition(points,posts,shifts);
            Set<UUID> shiftIds=shifts.stream().map(s->s.id).collect(Collectors.toSet());
            long assigned=shiftIds.isEmpty()?0:OperationalAssignmentEntity.count("instanceCountryId=?1 and assignmentPlanId=?2 and shiftOccurrenceId in ?3 and status<>'REMOVED'",tenant.instanceCountryId(),plan.id,shiftIds);
            int req=shifts.size(), asg=(int)assigned, un=Math.max(req-asg,0); double pct=req==0?100d:roundPct(100d*asg/req);
            result.add(new CoverageCompanyDto(c.id,c.name,posts.size(),req,asg,un,pct,plan.publishedRequiredShifts,plan.publishedAssignedShifts,plan.publishedCoveragePct==null?null:plan.publishedCoveragePct.doubleValue(),plan.status)); totalReq+=req;totalAssigned+=asg;
        }
        int un=Math.max(totalReq-totalAssigned,0); double pct=totalReq==0?100d:roundPct(100d*totalAssigned/totalReq);
        return new CoverageResponse(week,result,totalReq,totalAssigned,un,pct);
    }

    private AssignmentPlanEntity ensurePlan(UUID companyId, LocalDate week) {
        scope.requireCompany(companyId);
        Company c=Company.find("id=?1 and instanceCountryId=?2",companyId,tenant.instanceCountryId()).firstResult();
        if(c==null) throw new NotFoundException("Compañía no encontrada");
        AssignmentPlanEntity plan=AssignmentPlanEntity.find("instanceCountryId=?1 and companyId=?2 and weekStart=?3",tenant.instanceCountryId(),companyId,week).firstResult();
        if(plan==null){plan=new AssignmentPlanEntity();plan.instanceCountryId=tenant.instanceCountryId();plan.companyId=companyId;plan.weekStart=week;plan.status="DRAFT";plan.persist();}
        materializeShifts(companyId,week);
        closeIfExpired(plan);
        return plan;
    }

    private void materializeShifts(UUID companyId, LocalDate week){
        List<PointEntity> points=PointEntity.list("instanceCountryId=?1 and companyId=?2 and status='ACTIVE'",tenant.instanceCountryId(),companyId);
        Set<UUID> pointIds=points.stream().map(p->p.id).collect(Collectors.toSet());
        if(pointIds.isEmpty()) return;
        List<PostEntity> posts=PostEntity.list("instanceCountryId=?1 and pointId in ?2",tenant.instanceCountryId(),pointIds);
        Set<UUID> postIds=posts.stream().map(p->p.id).collect(Collectors.toSet());
        if(postIds.isEmpty()) return;
        List<PostShiftTemplate> templates=PostShiftTemplate.list("instanceCountryId=?1 and postId in ?2 and active=true",tenant.instanceCountryId(),postIds);
        for(PostShiftTemplate t:templates){
            for(int i=0;i<7;i++){
                if((t.dayMask & (1<<i))==0) continue;
                LocalDate d=week.plusDays(i);
                long exists=ShiftOccurrenceEntity.count("instanceCountryId=?1 and templateId=?2 and localDate=?3",tenant.instanceCountryId(),t.id,d);
                if(exists>0) continue;
                ZonedDateTime start=d.atTime(t.startTime).atZone(OPERATING_ZONE);
                LocalDate endDate=!t.endTime.isAfter(t.startTime)?d.plusDays(1):d;
                ZonedDateTime end=endDate.atTime(t.endTime).atZone(OPERATING_ZONE);
                ShiftOccurrenceEntity s=new ShiftOccurrenceEntity();s.instanceCountryId=tenant.instanceCountryId();s.postId=t.postId;s.templateId=t.id;s.localDate=d;s.shiftCode=t.shiftCode;s.shiftName=t.shiftName;s.startsAt=start.toInstant();s.endsAt=end.toInstant();s.commercialVersion=t.commercialVersion;s.required=true;s.persist();
            }
        }
    }

    private List<ValidationProblem> blockers(AssignmentPlanEntity plan, ShiftOccurrenceEntity shift, EmployeeOperationalSnapshot employee){
        List<ValidationProblem> problems=new ArrayList<>();
        if(!Objects.equals(employee.companyId,plan.companyId)) problems.add(new ValidationProblem("COMPANY","El colaborador no pertenece a la Compañía del plan."));
        if(!"ACTIVE".equals(employee.employmentStatus)) problems.add(new ValidationProblem("EMPLOYMENT_STATUS","El colaborador no está activo en SIC: RRHH."));
        if(!rolePolicy.isAssignable(employee.roleCode)) problems.add(new ValidationProblem("ROLE_NOT_ASSIGNABLE","El cargo del colaborador no está habilitado para Asignaciones."));
        if(openTransfer(employee.employeeId)!=null) problems.add(new ValidationProblem("TRANSFER_PENDING","El colaborador tiene una transferencia de Compañía pendiente y no admite nuevas asignaciones futuras."));
        long unav=EmployeeUnavailabilitySnapshot.count("instanceCountryId=?1 and employeeId=?2 and sourceStatus='ACTIVE' and startsAt<?3 and endsAt>?4",tenant.instanceCountryId(),employee.employeeId,shift.endsAt,shift.startsAt);
        if(unav>0) problems.add(new ValidationProblem("UNAVAILABLE","SIC: RRHH reporta vacaciones, permiso médico u otra indisponibilidad para este intervalo."));

        // La planificación puede cruzar semanas calendario (p.ej. ciclo 6-2 = 8 días).
        // Buscar por intervalo temporal, no por AssignmentPlan: así domingo/lunes se valida
        // correctamente sin recorrer el historial completo del colaborador en cada evaluación.
        List<ShiftOccurrenceEntity> adjacentShifts=ShiftOccurrenceEntity.list(
            "instanceCountryId=?1 and startsAt<=?2 and endsAt>=?3",
            tenant.instanceCountryId(),shift.endsAt,shift.startsAt);
        Set<UUID> adjacentShiftIds=adjacentShifts.stream().map(s->s.id).collect(Collectors.toSet());
        if(!adjacentShiftIds.isEmpty()){
            List<OperationalAssignmentEntity> employeeAssignments=OperationalAssignmentEntity.list(
                "instanceCountryId=?1 and status<>'REMOVED' and shiftOccurrenceId in ?2 and (employeeId=?3 or actualEmployeeId=?3)",
                tenant.instanceCountryId(),adjacentShiftIds,employee.employeeId);
            Map<UUID,ShiftOccurrenceEntity> map=adjacentShifts.stream().collect(Collectors.toMap(s->s.id,s->s));
            for(OperationalAssignmentEntity a:employeeAssignments){
                if(a.shiftOccurrenceId.equals(shift.id)) continue;
                ShiftOccurrenceEntity other=map.get(a.shiftOccurrenceId); if(other==null) continue;
                boolean overlap=other.startsAt.isBefore(shift.endsAt)&&other.endsAt.isAfter(shift.startsAt);
                if(overlap){problems.add(new ValidationProblem("OVERLAP","Existe otra asignación que se solapa con este turno."));break;}
                boolean consecutive=other.endsAt.equals(shift.startsAt)||shift.endsAt.equals(other.startsAt);
                if(consecutive){problems.add(new ValidationProblem("AUTO_RELEVO","La asignación produciría auto-relevo/turnos inmediatamente consecutivos."));break;}
            }
        }
        return problems;
    }

    private List<String> warnings(PostEntity post, EmployeeOperationalSnapshot employee){
        PostSkillRequirement req=PostSkillRequirement.find("instanceCountryId=?1 and postId=?2",tenant.instanceCountryId(),post.id).firstResult();
        return warnings(post,employee,req);
    }
    private List<String> warnings(PostEntity post, EmployeeOperationalSnapshot employee, PostSkillRequirement req){
        List<String> warnings=new ArrayList<>();
        if(req!=null&&!rolePolicy.matchesRequiredRole(req.requiredRoleCode,employee.roleCode)) warnings.add("Rol distinto al perfil preferido del Puesto: alerta de compatibilidad, no bloqueo.");
        if(employee.requiredChange) warnings.add("Colaborador marcado Cambio Requerido: alerta, no bloqueo mientras siga activo.");
        double min=tierMinimum(post.tier);
        if(employee.idScore!=null&&employee.idScore.doubleValue()<min) warnings.add("ID "+employee.idScore.stripTrailingZeros().toPlainString()+" menor al mínimo TIER "+post.tier+" ("+min+"): alerta, no bloqueo.");
        return warnings;
    }

    private BigDecimal compatibility(UUID postId, UUID employeeId){
        EmployeeSkillSnapshot a=EmployeeSkillSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        PostSkillRequirement r=PostSkillRequirement.find("instanceCountryId=?1 and postId=?2",tenant.instanceCountryId(),postId).firstResult();
        return compatibility(a,r);
    }
    private BigDecimal compatibility(EmployeeSkillSnapshot a, PostSkillRequirement r){
        if(a==null||r==null) return null;
        double[] av=a.values(),rv=r.values(); double numerator=0,denominator=0;
        for(int i=0;i<rv.length;i++){numerator+=Math.min(av[i],rv[i]);denominator+=rv[i];}
        double pct=denominator==0?100:Math.min(100,100*numerator/denominator);
        return BigDecimal.valueOf(pct).setScale(1,RoundingMode.HALF_UP);
    }

    private List<ShiftOccurrenceEntity> planShifts(AssignmentPlanEntity plan){
        List<PointEntity> points=PointEntity.list("instanceCountryId=?1 and companyId=?2 and status='ACTIVE'",tenant.instanceCountryId(),plan.companyId);
        Set<UUID> pids=points.stream().map(p->p.id).collect(Collectors.toSet());
        if(pids.isEmpty()) return List.of();
        List<PostEntity> posts=PostEntity.list("instanceCountryId=?1 and pointId in ?2",tenant.instanceCountryId(),pids); Set<UUID> postIds=posts.stream().map(p->p.id).collect(Collectors.toSet());
        if(postIds.isEmpty()) return List.of();
        Instant from=plan.weekStart.atStartOfDay(OPERATING_ZONE).toInstant(),to=plan.weekStart.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();
        List<ShiftOccurrenceEntity> shifts=ShiftOccurrenceEntity.list("instanceCountryId=?1 and postId in ?2 and startsAt>=?3 and startsAt<?4 order by startsAt",tenant.instanceCountryId(),postIds,from,to);
        return eligibleShiftsAfterServiceTransition(points,posts,shifts);
    }

    private List<ShiftOccurrenceEntity> eligibleShiftsAfterServiceTransition(List<PointEntity> points,List<PostEntity> posts,List<ShiftOccurrenceEntity> shifts){
        if(shifts.isEmpty()||points.isEmpty()||posts.isEmpty()) return shifts;
        Map<UUID,PointEntity> pointMap=points.stream().collect(Collectors.toMap(p->p.id,p->p));
        Map<UUID,UUID> pointByPost=posts.stream().collect(Collectors.toMap(p->p.id,p->p.pointId));
        return shifts.stream().filter(shift->{UUID pointId=pointByPost.get(shift.postId);PointEntity point=pointId==null?null:pointMap.get(pointId);return point==null||point.operationalTransitionUntil==null||!shift.startsAt.isBefore(point.operationalTransitionUntil);}).toList();
    }

    private Map<String,Object> publicationAssignmentSnapshot(OperationalAssignmentEntity a){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("assignmentId",a.id);m.put("shiftOccurrenceId",a.shiftOccurrenceId);m.put("employeeId",a.employeeId);m.put("ic",a.compatibilityIndex);m.put("idScore",a.idScore);
        if(a.employeeId!=null){
            EmployeeOperationalSnapshot e=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),a.employeeId).firstResult();
            EmployeeSkillSnapshot skills=EmployeeSkillSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),a.employeeId).firstResult();
            ShiftOccurrenceEntity shift=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",a.shiftOccurrenceId,tenant.instanceCountryId()).firstResult();
            if(e!=null){m.put("roleCode",e.roleCode);m.put("employmentStatus",e.employmentStatus);m.put("requiredChange",e.requiredChange);m.put("rrhhSourceAt",e.updatedFromSourceAt);}
            if(skills!=null){m.put("skills",skillDtos(skills));m.put("skillsSourceVersion",skills.sourceVersion);}
            if(shift!=null){
                List<EmployeeUnavailabilitySnapshot> u=EmployeeUnavailabilitySnapshot.list("instanceCountryId=?1 and employeeId=?2 and startsAt<?3 and endsAt>?4 and sourceStatus='ACTIVE' order by startsAt",tenant.instanceCountryId(),a.employeeId,shift.endsAt,shift.startsAt);
                m.put("unavailability",u.stream().map(this::unavailabilityDto).toList());
            }
        }
        return m;
    }

    private PublishResponse publishResponse(AssignmentPlanEntity plan){
        List<ShiftOccurrenceEntity> shifts=planShifts(plan); Set<UUID> ids=shifts.stream().map(s->s.id).collect(Collectors.toSet());
        int assigned=ids.isEmpty()?0:(int)OperationalAssignmentEntity.count("instanceCountryId=?1 and assignmentPlanId=?2 and shiftOccurrenceId in ?3 and status<>'REMOVED'",tenant.instanceCountryId(),plan.id,ids);
        return new PublishResponse(plan.id,plan.status,plan.publishedAt,shifts.size(),assigned,Math.max(shifts.size()-assigned,0));
    }

    private void recordEvent(AssignmentPlanEntity plan, OperationalAssignmentEntity assignment, ShiftOccurrenceEntity shift, String type, UUID original, UUID next, String actor, String reason, Object payload){
        AssignmentEventEntity e=new AssignmentEventEntity();e.instanceCountryId=tenant.instanceCountryId();e.assignmentPlanId=plan.id;e.assignmentId=assignment==null?null:assignment.id;e.shiftOccurrenceId=shift==null?(assignment==null?null:assignment.shiftOccurrenceId):shift.id;e.eventType=type;e.originalEmployeeId=original;e.newEmployeeId=next;e.actorUsername=actor;e.reason=reason;e.payloadJson=json(payload);e.occurredAt=Instant.now();e.persist();
    }

    private AssignmentDto toAssignmentDto(OperationalAssignmentEntity a, EmployeeOperationalSnapshot employee){
        return new AssignmentDto(a.id,a.shiftOccurrenceId,a.employeeId,a.effectiveEmployeeId(),employee==null?"—":employee.fullName,a.status,a.compatibilityIndex,a.idScore,parseWarnings(a.warningJson),a.assignedByUsername,a.assignedAt,a.actualAssignedByUsername,a.actualAssignedAt,a.reassignmentReason);
    }

    private Map<UUID,EmployeeOperationalSnapshot> employeeMap(Set<UUID> ids){
        if(ids.isEmpty()) return Map.of();
        List<EmployeeOperationalSnapshot> employees = EmployeeOperationalSnapshot.list(
                "instanceCountryId=?1 and employeeId in ?2", tenant.instanceCountryId(), ids);
        return employees.stream().collect(Collectors.toMap(e -> e.employeeId, e -> e));
    }

    private Map<UUID,ShiftOccurrenceEntity> shiftMap(Set<UUID> ids){
        if(ids.isEmpty()) return Map.of();
        List<ShiftOccurrenceEntity> shifts = ShiftOccurrenceEntity.list(
                "instanceCountryId=?1 and id in ?2", tenant.instanceCountryId(), ids);
        return shifts.stream().collect(Collectors.toMap(s -> s.id, s -> s));
    }

    private Map<UUID,Double> assignedHoursByEmployee(UUID planId, Instant from, Instant to, Set<UUID> employeeIds){
        if(employeeIds==null||employeeIds.isEmpty())return Map.of();
        List<OperationalAssignmentEntity> assignments=OperationalAssignmentEntity.list("instanceCountryId=?1 and assignmentPlanId=?2 and status<>'REMOVED' and (employeeId in ?3 or actualEmployeeId in ?3)",tenant.instanceCountryId(),planId,employeeIds);
        Map<UUID,ShiftOccurrenceEntity> shifts=shiftMap(assignments.stream().map(a->a.shiftOccurrenceId).collect(Collectors.toSet()));
        Map<UUID,Double> out=new HashMap<>();
        for(OperationalAssignmentEntity a:assignments){UUID e=a.effectiveEmployeeId();ShiftOccurrenceEntity s=shifts.get(a.shiftOccurrenceId);if(e==null||s==null||s.startsAt.isBefore(from)||!s.startsAt.isBefore(to))continue;double h=Duration.between(s.startsAt,s.endsAt).toMinutes()/60d;out.merge(e,h,Double::sum);}return out;
    }

    private EmployeeCompanyTransfer openTransfer(UUID employeeId){return EmployeeCompanyTransfer.find("instanceCountryId=?1 and employeeId=?2 and status in ?3",tenant.instanceCountryId(),employeeId,List.of("PENDING_ACCEPTANCE","ACCEPTED_PENDING_EFFECTIVE")).firstResult();}
    private EmployeeCompanyTransfer transfer(UUID id){EmployeeCompanyTransfer t=EmployeeCompanyTransfer.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(t==null)throw new NotFoundException("Transferencia no encontrada");return t;}
    private EmployeeOperationalSnapshot employeeSnapshot(UUID id){EmployeeOperationalSnapshot e=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),id).firstResult();if(e==null)throw new NotFoundException("Colaborador no encontrado");return e;}
    private Company company(UUID id){Company c=Company.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(c==null)throw new NotFoundException("Compañía no encontrada");return c;}
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}

    private TransferMiniDto transferMini(EmployeeCompanyTransfer t,String direction){Company o=company(t.originCompanyId),d=company(t.destinationCompanyId);return new TransferMiniDto(t.id,direction,t.status,o.id,o.name,d.id,d.name,t.reasonCode,t.reasonLabelSnapshot,t.observations,t.initiatedByUsername,t.initiatedAt,t.effectiveAt,t.releasedFutureAssignments);}
    private TransferMiniDto transferMini(EmployeeCompanyTransfer t,String direction,Map<UUID,Company> companies){Company o=companies.get(t.originCompanyId),d=companies.get(t.destinationCompanyId);if(o==null||d==null)return transferMini(t,direction);return new TransferMiniDto(t.id,direction,t.status,o.id,o.name,d.id,d.name,t.reasonCode,t.reasonLabelSnapshot,t.observations,t.initiatedByUsername,t.initiatedAt,t.effectiveAt,t.releasedFutureAssignments);}
    private Map<UUID,Company> companyMap(Set<UUID> ids){if(ids==null||ids.isEmpty())return Map.of();return Company.<Company>list("instanceCountryId=?1 and id in ?2",tenant.instanceCountryId(),ids).stream().collect(Collectors.toMap(c->c.id,c->c));}
    private CompanyTransferDto transferDto(EmployeeCompanyTransfer t,EmployeeOperationalSnapshot e){Company o=company(t.originCompanyId),d=company(t.destinationCompanyId);return new CompanyTransferDto(t.id,t.employeeId,e.fullName,e.roleCode,e.idScore,o.id,o.name,d.id,d.name,t.reasonCode,t.reasonLabelSnapshot,t.observations,t.status,t.initiatedByUsername,t.initiatedAt,t.decisionByUsername,t.decisionAt,t.decisionNote,t.effectiveAt,t.releasedFutureAssignments,t.rrhhSyncStatus);}

    private void requireDestinationDecision(EmployeeCompanyTransfer t){Company destination=company(t.destinationCompanyId);if(destination.alwaysActive||"COORDINATION".equals(destination.companyType)){if(!scope.canManageKaibilTransferAgainst(t.originCompanyId))throw new ForbiddenException("El usuario no puede aceptar transferencias hacia Kaibil desde esta Compañía.");}else scope.requireEditorForCompany(destination.id);}

    private OperationalAssignmentEntity currentAssignment(UUID employeeId,UUID companyId,Instant now){
        List<AssignmentPlanEntity> plans=AssignmentPlanEntity.list("instanceCountryId=?1 and companyId=?2",tenant.instanceCountryId(),companyId);if(plans.isEmpty())return null;Set<UUID> planIds=plans.stream().map(p->p.id).collect(Collectors.toSet());
        List<OperationalAssignmentEntity> rows=OperationalAssignmentEntity.list("instanceCountryId=?1 and assignmentPlanId in ?2 and status<>'REMOVED' and (employeeId=?3 or actualEmployeeId=?3)",tenant.instanceCountryId(),planIds,employeeId);if(rows.isEmpty())return null;Map<UUID,ShiftOccurrenceEntity> shifts=shiftMap(rows.stream().map(a->a.shiftOccurrenceId).collect(Collectors.toSet()));
        return rows.stream().filter(a->{ShiftOccurrenceEntity sh=shifts.get(a.shiftOccurrenceId);return sh!=null&&!now.isBefore(sh.startsAt)&&now.isBefore(sh.endsAt);}).findFirst().orElse(null);
    }

    private int releaseFutureAssignments(UUID employeeId,UUID companyId,UUID transferId){
        Instant now=Instant.now();List<AssignmentPlanEntity> plans=AssignmentPlanEntity.list("instanceCountryId=?1 and companyId=?2",tenant.instanceCountryId(),companyId);if(plans.isEmpty())return 0;Set<UUID> planIds=plans.stream().map(p->p.id).collect(Collectors.toSet());
        List<OperationalAssignmentEntity> rows=OperationalAssignmentEntity.list("instanceCountryId=?1 and assignmentPlanId in ?2 and status<>'REMOVED' and (employeeId=?3 or actualEmployeeId=?3)",tenant.instanceCountryId(),planIds,employeeId);Map<UUID,ShiftOccurrenceEntity> shifts=shiftMap(rows.stream().map(a->a.shiftOccurrenceId).collect(Collectors.toSet()));Map<UUID,AssignmentPlanEntity> planMap=plans.stream().collect(Collectors.toMap(p->p.id,p->p));int released=0;
        for(OperationalAssignmentEntity a:rows){ShiftOccurrenceEntity sh=shifts.get(a.shiftOccurrenceId);if(sh==null||!sh.startsAt.isAfter(now))continue;UUID before=a.effectiveEmployeeId();a.status="REMOVED";AssignmentPlanEntity plan=planMap.get(a.assignmentPlanId);if(plan!=null)recordEvent(plan,a,sh,"ASSIGNMENT_RELEASED_FOR_COMPANY_TRANSFER",before,null,identity.getPrincipal().getName(),"Transferencia de Compañía pendiente",Map.of("transferId",transferId));released++;}
        return released;
    }

    private void completeTransfer(EmployeeCompanyTransfer t,Instant effectiveAt){
        EmployeeOperationalSnapshot e=employeeSnapshot(t.employeeId);Instant now=Instant.now();
        CompanyMembershipEntity current=CompanyMembershipEntity.find("instanceCountryId=?1 and employeeId=?2 and membershipType='PRIMARY' and endsAt is null",tenant.instanceCountryId(),t.employeeId).firstResult();
        // The active PRIMARY membership is protected by a partial unique index. Flush the
        // closure of the current membership before inserting the destination membership;
        // otherwise Hibernate may schedule the INSERT first and PostgreSQL rejects the
        // transaction as a duplicate active PRIMARY membership.
        if(current!=null){current.endsAt=effectiveAt;current.persistAndFlush();}
        CompanyMembershipEntity next=new CompanyMembershipEntity();next.instanceCountryId=tenant.instanceCountryId();next.companyId=t.destinationCompanyId;next.employeeId=t.employeeId;next.membershipType="PRIMARY";next.roleCode=e.roleCode;next.startsAt=effectiveAt;next.requiredChange=e.requiredChange;next.persist();
        e.companyId=t.destinationCompanyId;e.updatedFromSourceAt=now;t.status="EFFECTIVE";t.effectiveAt=effectiveAt;t.completedAt=now;t.rrhhSyncStatus="ACKNOWLEDGED_UAT";
        OutboxEvent.of(tenant.instanceCountryId(),"EMPLOYEE_COMPANY_TRANSFER",t.id,"RRHH_COMPANY_MEMBERSHIP_CHANGE_REQUESTED",json(Map.of("employeeId",t.employeeId,"originCompanyId",t.originCompanyId,"destinationCompanyId",t.destinationCompanyId,"effectiveAt",effectiveAt))).persist();
    }

    private void reconcileDueTransfers(){List<EmployeeCompanyTransfer> due=EmployeeCompanyTransfer.list("instanceCountryId=?1 and status='ACCEPTED_PENDING_EFFECTIVE' and effectiveAt<=?2",tenant.instanceCountryId(),Instant.now());for(EmployeeCompanyTransfer t:due)completeTransfer(t,t.effectiveAt==null?Instant.now():t.effectiveAt);}

    private List<SkillDto> skillDtos(EmployeeSkillSnapshot s){
        return List.of(new SkillDto("ATTENDANCE","Asistencia",s.skillDiscipline),new SkillDto("TACTICAL","Condición táctica",s.skillTactical),new SkillDto("ACCESS","Control de acceso",s.skillAccess),new SkillDto("PORTE","Porte Cajamarca",s.skillCommunication),new SkillDto("PATROL","Patrullas operativas",s.skillPatrol),new SkillDto("LEADERSHIP","Liderazgo",s.skillResponse),new SkillDto("CRITERION","Criterio operativo",s.skillObservation),new SkillDto("CUSTOMER","Atención al cliente",s.skillCustomer));
    }
    private List<CompatibilitySkillDto> compatibilitySkills(EmployeeSkillSnapshot a,PostSkillRequirement r){ if(a==null||r==null)return List.of(); return List.of(
        cskill("ATTENDANCE","Asistencia",a.skillDiscipline,r.skillDiscipline),cskill("TACTICAL","Condición táctica",a.skillTactical,r.skillTactical),cskill("ACCESS","Control de acceso",a.skillAccess,r.skillAccess),cskill("PORTE","Porte Cajamarca",a.skillCommunication,r.skillCommunication),cskill("PATROL","Patrullas operativas",a.skillPatrol,r.skillPatrol),cskill("LEADERSHIP","Liderazgo",a.skillResponse,r.skillResponse),cskill("CRITERION","Criterio operativo",a.skillObservation,r.skillObservation),cskill("CUSTOMER","Atención al cliente",a.skillCustomer,r.skillCustomer)); }
    private CompatibilitySkillDto cskill(String code,String label,BigDecimal actual,BigDecimal required){return new CompatibilitySkillDto(code,label,actual,required,actual!=null&&required!=null&&actual.compareTo(required)>=0);}
    private List<PostRequirementDto> postRequirementDtos(PostSkillRequirement r){ if(r==null)return List.of(); return List.of(
        new PostRequirementDto("ATTENDANCE","Asistencia",r.skillDiscipline),new PostRequirementDto("TACTICAL","Condición táctica",r.skillTactical),new PostRequirementDto("ACCESS","Control de acceso",r.skillAccess),new PostRequirementDto("PORTE","Porte Cajamarca",r.skillCommunication),new PostRequirementDto("PATROL","Patrullas operativas",r.skillPatrol),new PostRequirementDto("LEADERSHIP","Liderazgo",r.skillResponse),new PostRequirementDto("CRITERION","Criterio operativo",r.skillObservation),new PostRequirementDto("CUSTOMER","Atención al cliente",r.skillCustomer)); }

    private UnavailabilityDto unavailabilityDto(EmployeeUnavailabilitySnapshot u){return new UnavailabilityDto(u.type,u.startsAt,u.endsAt,u.sourceRef,u.sourceReasonLabel);}
    private PlanDto planDto(AssignmentPlanEntity p){return new PlanDto(p.id,p.companyId,p.weekStart,p.status,p.publishedAt,p.publishedByUsername,p.draftSavedAt,p.draftSavedByUsername);}
    private AssignmentPlanEntity plan(UUID id){AssignmentPlanEntity p=AssignmentPlanEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Plan no encontrado");closeIfExpired(p);return p;}
    private void closeIfExpired(AssignmentPlanEntity p){if(!"PUBLISHED".equals(p.status))return;Instant end=p.weekStart.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();if(Instant.now().isBefore(end))return;p.status="CLOSED";p.closedAt=Instant.now();recordEvent(p,null,null,"ASSIGNMENT_PLAN_CLOSED",null,null,"SYSTEM","Cierre automático al finalizar la semana operativa",Map.of("reason","WEEK_ENDED"));OutboxEvent.of(p.instanceCountryId,"ASSIGNMENT_PLAN",p.id,"ASSIGNMENT_PLAN_CLOSED",json(Map.of("planId",p.id,"reason","WEEK_ENDED"))).persist();}
    private LocalDate parseDate(String value){try{return value==null||value.isBlank()?LocalDate.now(OPERATING_ZONE):LocalDate.parse(value);}catch(Exception e){throw new BadRequestException("weekStart inválido. Use YYYY-MM-DD");}}
    private LocalDate normalizeWeek(LocalDate d){LocalDate base=d==null?LocalDate.now(OPERATING_ZONE):d;return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
    private double tierMinimum(String tier){return switch(tier==null?"":tier.toUpperCase(Locale.ROOT)){case "IV"->9.5;case "III"->8.5;case "II"->7.5;default->6.5;};}
    private static double roundPct(double v){return Math.round(v*10d)/10d;}
    private static double roundOne(double v){return Math.round(v*10d)/10d;}
    private static String blankToDefault(String v,String d){return v==null||v.isBlank()?d:v.trim();}
    private static String roleLabel(String code){return switch(code){case "AGENTE_SEGURIDAD"->"Agente de Seguridad";case "SUPERVISOR_SEGURIDAD"->"Supervisor de Seguridad";case "ESCOLTA_SEGURIDAD"->"Escolta";default->code;};}
    private static Map<String,Object> assignmentPayload(List<String> warnings,BigDecimal ic,BigDecimal idScore){Map<String,Object> payload=new LinkedHashMap<>();payload.put("warnings",warnings);payload.put("ic",ic);payload.put("idScore",idScore);return payload;}

    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new InternalServerErrorException("No se pudo serializar auditoría",e);}}
    private List<String> parseWarnings(String value){if(value==null||value.isBlank())return List.of();try{return mapper.readValue(value,mapper.getTypeFactory().constructCollectionType(List.class,String.class));}catch(Exception e){return List.of();}}
    private WebApplicationException conflict(String code,String message){return new WebApplicationException(Response.status(Response.Status.CONFLICT).entity(Map.of("blocking",true,"problems",List.of(new ValidationProblem(code,message)))).type(MediaType.APPLICATION_JSON).build());}
}
