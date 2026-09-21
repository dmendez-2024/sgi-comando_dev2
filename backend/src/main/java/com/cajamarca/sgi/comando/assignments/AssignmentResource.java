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
    private static final Set<String> EDIT_ROLES = Set.of("COORDINADOR_COMPANIA","ASISTENTE_COORDINACION");

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject ObjectMapper mapper;
    @Inject AssignmentScopeService scope;

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
    public record UnavailabilityDto(String type, Instant startsAt, Instant endsAt, String sourceRef) {}
    public record PersonnelDto(UUID employeeId, String fullName, String roleCode, String employmentStatus, BigDecimal idScore, String preferredShift,
                               boolean requiredChange, double assignedHours, List<UnavailabilityDto> unavailability) {}
    public record PersonnelPage(List<PersonnelDto> items, long total, int page, int size) {}
    public record SkillDto(String code, String label, BigDecimal level) {}
    public record EmployeeDetails(UUID employeeId, String fullName, String roleCode, String employmentStatus, BigDecimal idScore, String preferredShift,
                                  boolean requiredChange, List<SkillDto> skills, List<UnavailabilityDto> unavailability, List<AssignmentAuditDto> recentAssignments) {}
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
        requireEditor();
        if(req==null || req.companyId()==null) throw new BadRequestException("companyId obligatorio");
        LocalDate week=normalizeWeek(req.weekStart());
        AssignmentPlanEntity plan=ensurePlan(req.companyId(),week);
        return planDto(plan);
    }

    @POST
    @Path("/plans/{planId}/save-draft")
    @Transactional
    public DraftSaveResponse saveDraft(@PathParam("planId") UUID planId) {
        requireEditor(); AssignmentPlanEntity plan=plan(planId);
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
                                   @QueryParam("page") @DefaultValue("0") int page,
                                   @QueryParam("size") @DefaultValue("50") int size) {
        if(companyId==null) throw new BadRequestException("companyId obligatorio");
        scope.requireCompany(companyId);
        LocalDate week=normalizeWeek(parseDate(weekStart));
        int safePage=Math.max(page,0), safeSize=Math.min(Math.max(size,1),100);
        StringBuilder jpql=new StringBuilder("instanceCountryId=?1 and companyId=?2");
        List<Object> params=new ArrayList<>(); params.add(tenant.instanceCountryId()); params.add(companyId);
        int idx=3;
        if(q!=null&&!q.isBlank()){jpql.append(" and lower(fullName) like ?").append(idx);params.add("%"+q.trim().toLowerCase(Locale.ROOT)+"%");idx++;}
        if(role!=null&&!role.isBlank()){jpql.append(" and roleCode=?").append(idx);params.add(role);}
        jpql.append(" order by fullName");
        var query=EmployeeOperationalSnapshot.find(jpql.toString(),params.toArray());
        long total=query.count();
        List<EmployeeOperationalSnapshot> employees=query.page(Page.of(safePage,safeSize)).list();
        if(employees.isEmpty()) return new PersonnelPage(List.of(),total,safePage,safeSize);

        Set<UUID> employeeIds=employees.stream().map(e->e.employeeId).collect(Collectors.toSet());
        Instant from=week.atStartOfDay(OPERATING_ZONE).toInstant();
        Instant to=week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();
        List<EmployeeUnavailabilitySnapshot> unavs=EmployeeUnavailabilitySnapshot.list("instanceCountryId=?1 and employeeId in ?2 and startsAt<?3 and endsAt>?4 and sourceStatus='ACTIVE' order by startsAt",tenant.instanceCountryId(),employeeIds,to,from);
        Map<UUID,List<EmployeeUnavailabilitySnapshot>> unavByEmployee=unavs.stream().collect(Collectors.groupingBy(u->u.employeeId));

        AssignmentPlanEntity plan=AssignmentPlanEntity.find("instanceCountryId=?1 and companyId=?2 and weekStart=?3",tenant.instanceCountryId(),companyId,week).firstResult();
        Map<UUID,Double> assignedHours=plan==null?Map.of():assignedHoursByEmployee(plan.id,from,to);
        List<PersonnelDto> items=employees.stream().map(e->new PersonnelDto(e.employeeId,e.fullName,e.roleCode,e.employmentStatus,e.idScore,e.preferredShift,e.requiredChange,
                roundOne(assignedHours.getOrDefault(e.employeeId,0d)),
                unavByEmployee.getOrDefault(e.employeeId,List.of()).stream().map(this::unavailabilityDto).toList())).toList();
        return new PersonnelPage(items,total,safePage,safeSize);
    }

    @GET
    @Path("/employees/{employeeId}")
    @Transactional
    public EmployeeDetails employee(@PathParam("employeeId") UUID employeeId,
                                    @QueryParam("weekStart") String weekStart) {
        EmployeeOperationalSnapshot e=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        if(e==null) throw new NotFoundException();
        EmployeeSkillSnapshot skills=EmployeeSkillSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        List<SkillDto> skillDtos=skills==null?List.of():skillDtos(skills);
        List<EmployeeUnavailabilitySnapshot> unavs=EmployeeUnavailabilitySnapshot.list("instanceCountryId=?1 and employeeId=?2 order by startsAt desc",tenant.instanceCountryId(),employeeId);

        List<OperationalAssignmentEntity> recent=OperationalAssignmentEntity.find("instanceCountryId=?1 and (employeeId=?2 or actualEmployeeId=?2) and status<>'REMOVED' order by updatedAt desc",tenant.instanceCountryId(),employeeId).page(Page.of(0,12)).list();
        Map<UUID,ShiftOccurrenceEntity> shifts=shiftMap(recent.stream().map(a->a.shiftOccurrenceId).collect(Collectors.toSet()));
        Set<UUID> postIds=shifts.values().stream().map(s->s.postId).collect(Collectors.toSet());
        Map<UUID,PostEntity> posts;
        if (postIds.isEmpty()) {
            posts = Map.of();
        } else {
            List<PostEntity> postList = PostEntity.list("instanceCountryId=?1 and id in ?2", tenant.instanceCountryId(), postIds);
            posts = postList.stream().collect(Collectors.toMap(p -> p.id, p -> p));
        }
        List<AssignmentAuditDto> audits=recent.stream().map(a->{
            ShiftOccurrenceEntity s=shifts.get(a.shiftOccurrenceId); PostEntity p=s==null?null:posts.get(s.postId);
            return new AssignmentAuditDto(p==null?"—":p.code,p==null?"—":p.name,s==null?"—":s.shiftName,s==null?null:s.startsAt,a.status,a.actualAssignedByUsername!=null?a.actualAssignedByUsername:a.assignedByUsername);
        }).toList();
        return new EmployeeDetails(e.employeeId,e.fullName,e.roleCode,e.employmentStatus,e.idScore,e.preferredShift,e.requiredChange,skillDtos,unavs.stream().map(this::unavailabilityDto).toList(),audits);
    }

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
        List<ShiftOccurrenceEntity> shifts=planShifts(plan);
        Map<UUID,PostEntity> posts;
        Set<UUID> postIds=shifts.stream().map(x->x.postId).collect(Collectors.toSet());
        if(postIds.isEmpty()) posts=Map.of(); else { List<PostEntity> list=PostEntity.list("instanceCountryId=?1 and id in ?2",tenant.instanceCountryId(),postIds); posts=list.stream().collect(Collectors.toMap(x->x.id,x->x)); }
        List<EvaluationDto> out=new ArrayList<>();
        for(ShiftOccurrenceEntity shift:shifts){
            PostEntity post=posts.get(shift.postId);
            List<ValidationProblem> hard=blockers(plan,shift,employee);
            List<String> soft=post==null?new ArrayList<>():warnings(post,employee);
            BigDecimal ic=post==null?null:compatibility(post.id,employee.employeeId);
            if(ic!=null&&ic.compareTo(BigDecimal.valueOf(100))<0) soft.add("IC "+ic.stripTrailingZeros().toPlainString()+"%: compatibilidad inferior a 100% (alerta, no bloqueo).");
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
        requireEditor();
        if(req==null||req.planId()==null||req.shiftOccurrenceId()==null||req.employeeId()==null) throw new BadRequestException("planId, shiftOccurrenceId y employeeId obligatorios");
        AssignmentPlanEntity plan=plan(req.planId());
        if("CLOSED".equals(plan.status)) throw conflict("PLAN_CERRADO","El plan está cerrado y no admite cambios.");
        ShiftOccurrenceEntity shift=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",req.shiftOccurrenceId(),tenant.instanceCountryId()).firstResult();
        if(shift==null) throw new NotFoundException("Turno requerido no encontrado");
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",shift.postId,tenant.instanceCountryId()).firstResult();
        if(post==null) throw new NotFoundException("Puesto no encontrado");
        PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",post.pointId,tenant.instanceCountryId()).firstResult();
        if(point==null||!Objects.equals(point.companyId,plan.companyId)) throw conflict("COMPANIA_INVALIDA","El turno no pertenece a la Compañía del plan.");
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
                recordEvent(plan,existing,shift,eventType,before,employee.employeeId,actor,existing.reassignmentReason,Map.of("warnings",warnings,"ic",ic,"idScore",employee.idScore));
            }
        }
        existing.compatibilityIndex=ic; existing.idScore=employee.idScore; existing.warningJson=json(warnings);
        if(!"REASSIGNMENT_CREATED".equals(eventType))
            recordEvent(plan,existing,shift,eventType,existing.employeeId,employee.employeeId,actor,req.reason(),Map.of("warnings",warnings,"ic",ic,"idScore",employee.idScore));
        return Response.ok(toAssignmentDto(existing,employee)).build();
    }

    @DELETE
    @Path("/{assignmentId}")
    @Transactional
    public Response remove(@PathParam("assignmentId") UUID assignmentId) {
        requireEditor();
        OperationalAssignmentEntity a=OperationalAssignmentEntity.find("id=?1 and instanceCountryId=?2",assignmentId,tenant.instanceCountryId()).firstResult();
        if(a==null) throw new NotFoundException();
        AssignmentPlanEntity plan=plan(a.assignmentPlanId);
        if(!"DRAFT".equals(plan.status)) throw conflict("PLAN_PUBLICADO","Un plan publicado no se edita silenciosamente; utilice Reasignación.");
        a.status="REMOVED";
        recordEvent(plan,a,null,"ASSIGNMENT_REMOVED",a.employeeId,null,identity.getPrincipal().getName(),null,Map.of());
        return Response.noContent().build();
    }

    @POST
    @Path("/plans/{planId}/publish")
    @Transactional
    public PublishResponse publish(@PathParam("planId") UUID planId) {
        requireEditor();
        AssignmentPlanEntity plan=plan(planId);
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
        List<String> warnings=new ArrayList<>();
        PostSkillRequirement req=PostSkillRequirement.find("instanceCountryId=?1 and postId=?2",tenant.instanceCountryId(),post.id).firstResult();
        if(req!=null&&!Objects.equals(req.requiredRoleCode,employee.roleCode)) warnings.add("Rol distinto al perfil preferido del Puesto: alerta de compatibilidad, no bloqueo.");
        if(employee.requiredChange) warnings.add("Colaborador marcado Cambio Requerido: alerta, no bloqueo mientras siga activo.");
        double min=tierMinimum(post.tier);
        if(employee.idScore!=null&&employee.idScore.doubleValue()<min) warnings.add("ID "+employee.idScore.stripTrailingZeros().toPlainString()+" menor al mínimo TIER "+post.tier+" ("+min+"): alerta, no bloqueo.");
        return warnings;
    }

    private BigDecimal compatibility(UUID postId, UUID employeeId){
        EmployeeSkillSnapshot a=EmployeeSkillSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();
        PostSkillRequirement r=PostSkillRequirement.find("instanceCountryId=?1 and postId=?2",tenant.instanceCountryId(),postId).firstResult();
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
        return ShiftOccurrenceEntity.list("instanceCountryId=?1 and postId in ?2 and startsAt>=?3 and startsAt<?4 order by startsAt",tenant.instanceCountryId(),postIds,from,to);
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

    private Map<UUID,Double> assignedHoursByEmployee(UUID planId, Instant from, Instant to){
        List<OperationalAssignmentEntity> assignments=OperationalAssignmentEntity.list("instanceCountryId=?1 and assignmentPlanId=?2 and status<>'REMOVED'",tenant.instanceCountryId(),planId);
        Map<UUID,ShiftOccurrenceEntity> shifts=shiftMap(assignments.stream().map(a->a.shiftOccurrenceId).collect(Collectors.toSet()));
        Map<UUID,Double> out=new HashMap<>();
        for(OperationalAssignmentEntity a:assignments){UUID e=a.effectiveEmployeeId();ShiftOccurrenceEntity s=shifts.get(a.shiftOccurrenceId);if(e==null||s==null||s.startsAt.isBefore(from)||!s.startsAt.isBefore(to))continue;double h=Duration.between(s.startsAt,s.endsAt).toMinutes()/60d;out.merge(e,h,Double::sum);}return out;
    }

    private List<SkillDto> skillDtos(EmployeeSkillSnapshot s){
        return List.of(new SkillDto("ATTENDANCE","Asistencia",s.skillDiscipline),new SkillDto("TACTICAL","Condición táctica",s.skillTactical),new SkillDto("ACCESS","Control de acceso",s.skillAccess),new SkillDto("PORTE","Porte Cajamarca",s.skillCommunication),new SkillDto("PATROL","Patrullas operativas",s.skillPatrol),new SkillDto("LEADERSHIP","Liderazgo",s.skillResponse),new SkillDto("CRITERION","Criterio operativo",s.skillObservation),new SkillDto("CUSTOMER","Atención al cliente",s.skillCustomer));
    }
    private List<CompatibilitySkillDto> compatibilitySkills(EmployeeSkillSnapshot a,PostSkillRequirement r){ if(a==null||r==null)return List.of(); return List.of(
        cskill("ATTENDANCE","Asistencia",a.skillDiscipline,r.skillDiscipline),cskill("TACTICAL","Condición táctica",a.skillTactical,r.skillTactical),cskill("ACCESS","Control de acceso",a.skillAccess,r.skillAccess),cskill("PORTE","Porte Cajamarca",a.skillCommunication,r.skillCommunication),cskill("PATROL","Patrullas operativas",a.skillPatrol,r.skillPatrol),cskill("LEADERSHIP","Liderazgo",a.skillResponse,r.skillResponse),cskill("CRITERION","Criterio operativo",a.skillObservation,r.skillObservation),cskill("CUSTOMER","Atención al cliente",a.skillCustomer,r.skillCustomer)); }
    private CompatibilitySkillDto cskill(String code,String label,BigDecimal actual,BigDecimal required){return new CompatibilitySkillDto(code,label,actual,required,actual!=null&&required!=null&&actual.compareTo(required)>=0);}
    private List<PostRequirementDto> postRequirementDtos(PostSkillRequirement r){ if(r==null)return List.of(); return List.of(
        new PostRequirementDto("ATTENDANCE","Asistencia",r.skillDiscipline),new PostRequirementDto("TACTICAL","Condición táctica",r.skillTactical),new PostRequirementDto("ACCESS","Control de acceso",r.skillAccess),new PostRequirementDto("PORTE","Porte Cajamarca",r.skillCommunication),new PostRequirementDto("PATROL","Patrullas operativas",r.skillPatrol),new PostRequirementDto("LEADERSHIP","Liderazgo",r.skillResponse),new PostRequirementDto("CRITERION","Criterio operativo",r.skillObservation),new PostRequirementDto("CUSTOMER","Atención al cliente",r.skillCustomer)); }

    private UnavailabilityDto unavailabilityDto(EmployeeUnavailabilitySnapshot u){return new UnavailabilityDto(u.type,u.startsAt,u.endsAt,u.sourceRef);}
    private PlanDto planDto(AssignmentPlanEntity p){return new PlanDto(p.id,p.companyId,p.weekStart,p.status,p.publishedAt,p.publishedByUsername,p.draftSavedAt,p.draftSavedByUsername);}
    private AssignmentPlanEntity plan(UUID id){AssignmentPlanEntity p=AssignmentPlanEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Plan no encontrado");closeIfExpired(p);return p;}
    private void closeIfExpired(AssignmentPlanEntity p){if(!"PUBLISHED".equals(p.status))return;Instant end=p.weekStart.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();if(Instant.now().isBefore(end))return;p.status="CLOSED";p.closedAt=Instant.now();recordEvent(p,null,null,"ASSIGNMENT_PLAN_CLOSED",null,null,"SYSTEM","Cierre automático al finalizar la semana operativa",Map.of("reason","WEEK_ENDED"));OutboxEvent.of(p.instanceCountryId,"ASSIGNMENT_PLAN",p.id,"ASSIGNMENT_PLAN_CLOSED",json(Map.of("planId",p.id,"reason","WEEK_ENDED"))).persist();}
    private LocalDate parseDate(String value){try{return value==null||value.isBlank()?LocalDate.now(OPERATING_ZONE):LocalDate.parse(value);}catch(Exception e){throw new BadRequestException("weekStart inválido. Use YYYY-MM-DD");}}
    private LocalDate normalizeWeek(LocalDate d){LocalDate base=d==null?LocalDate.now(OPERATING_ZONE):d;return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
    private double tierMinimum(String tier){return switch(tier==null?"":tier.toUpperCase(Locale.ROOT)){case "IV"->9.5;case "III"->8.5;case "II"->7.5;default->6.5;};}
    private static double roundPct(double v){return Math.round(v*10d)/10d;}
    private static double roundOne(double v){return Math.round(v*10d)/10d;}
    private static String blankToDefault(String v,String d){return v==null||v.isBlank()?d:v.trim();}

    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new InternalServerErrorException("No se pudo serializar auditoría",e);}}
    private List<String> parseWarnings(String value){if(value==null||value.isBlank())return List.of();try{return mapper.readValue(value,mapper.getTypeFactory().constructCollectionType(List.class,String.class));}catch(Exception e){return List.of();}}
    private WebApplicationException conflict(String code,String message){return new WebApplicationException(Response.status(Response.Status.CONFLICT).entity(Map.of("blocking",true,"problems",List.of(new ValidationProblem(code,message)))).type(MediaType.APPLICATION_JSON).build());}
    private void requireEditor(){boolean ok=identity.getRoles().stream().anyMatch(EDIT_ROLES::contains);if(!ok)throw new ForbiddenException("Rol de solo lectura para planificación.");}
}
