package com.cajamarca.sgi.comando.postconfig;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.cajamarca.sgi.comando.storage.StandardReferenceImage;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;

@Path("/api/post-configurations/{postId}/visual-rule")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PostVisualRuleResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;

    public record RuleDto(Double thresholdValue,Integer referenceImageCount,int effectiveReferenceImageCount,int savedImageCount,Integer historicalPresentationMonths,String updatedBy,Instant updatedAt) {}
    public record SaveRequest(Double thresholdValue,Integer referenceImageCount,Integer historicalPresentationMonths) {}
    public record HistoryDto(Double thresholdValue,Integer referenceImageCount,Integer historicalPresentationMonths,String legacyHistoricalPresentation,String changedBy,Instant changedAt) {}

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public RuleDto get(@PathParam("postId") UUID postId){
        authorizedPost(postId);
        PostVisualRule rule=rule(postId);
        return dto(postId,rule);
    }

    @GET
    @Path("/history")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public List<HistoryDto> history(@PathParam("postId") UUID postId){
        authorizedPost(postId);
        List<PostVisualRuleHistory> rows=PostVisualRuleHistory.list("instanceCountryId=?1 and postId=?2 order by changedAt desc",tenant.instanceCountryId(),postId);
        return rows.stream().map(h->new HistoryDto(h.thresholdValue,h.referenceImageCount,h.historicalPresentationMonths,h.historicalPresentation,h.changedByUsername,h.changedAt)).toList();
    }

    @PUT
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public RuleDto save(@PathParam("postId") UUID postId,SaveRequest request){
        authorizedPost(postId);
        if(request==null) throw new BadRequestException("La regla visual es obligatoria");
        if(request.thresholdValue()!=null&&(!Double.isFinite(request.thresholdValue())||request.thresholdValue()<0||request.thresholdValue()>1)) throw new BadRequestException("El umbral debe estar entre 0 y 1");
        if(request.referenceImageCount()!=null&&(request.referenceImageCount()<1||request.referenceImageCount()>3)) throw new BadRequestException("Las fotos a enviar deben estar entre 1 y 3");
        if(request.historicalPresentationMonths()!=null&&(request.historicalPresentationMonths()<1||request.historicalPresentationMonths()>120)) throw new BadRequestException("La presentación histórica debe estar entre 1 y 120 meses");
        PostVisualRule rule=rule(postId);
        if(rule!=null&&Objects.equals(rule.thresholdValue,request.thresholdValue())&&Objects.equals(rule.referenceImageCount,request.referenceImageCount())&&Objects.equals(rule.historicalPresentationMonths,request.historicalPresentationMonths())) return dto(postId,rule);
        if(rule==null){rule=new PostVisualRule();rule.instanceCountryId=tenant.instanceCountryId();rule.postId=postId;}
        rule.thresholdValue=request.thresholdValue();
        rule.referenceImageCount=request.referenceImageCount();
        rule.historicalPresentationMonths=request.historicalPresentationMonths();
        rule.updatedByUsername=identity.getPrincipal().getName();
        if(rule.id==null) rule.persist();
        else rule.updatedAt=Instant.now();
        PostVisualRuleHistory history=new PostVisualRuleHistory();
        history.id=UUID.randomUUID();history.instanceCountryId=tenant.instanceCountryId();history.postId=postId;
        history.thresholdValue=rule.thresholdValue;history.referenceImageCount=rule.referenceImageCount;
        history.historicalPresentationMonths=rule.historicalPresentationMonths;history.changedByUsername=rule.updatedByUsername;history.changedAt=Instant.now();
        history.persist();
        return dto(postId,rule);
    }

    private PostVisualRule rule(UUID postId){return PostVisualRule.find("instanceCountryId=?1 and postId=?2",tenant.instanceCountryId(),postId).firstResult();}
    private RuleDto dto(UUID postId,PostVisualRule rule){
        int saved=(int)StandardReferenceImage.countOf(StandardReferenceImage.POST_CONFIG,postId);
        int effective=rule==null||rule.referenceImageCount==null?saved:Math.min(rule.referenceImageCount,saved);
        return new RuleDto(rule==null?null:rule.thresholdValue,rule==null?null:rule.referenceImageCount,effective,saved,rule==null?null:rule.historicalPresentationMonths,rule==null?null:rule.updatedByUsername,rule==null?null:rule.updatedAt);
    }
    private void authorizedPost(UUID postId){
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",postId,tenant.instanceCountryId()).firstResult();
        if(post==null) throw new NotFoundException("Puesto no encontrado");
        PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",post.pointId,tenant.instanceCountryId()).firstResult();
        if(point==null) throw new NotFoundException("Punto no encontrado");
        scope.requireCompany(point.companyId);
    }
}
