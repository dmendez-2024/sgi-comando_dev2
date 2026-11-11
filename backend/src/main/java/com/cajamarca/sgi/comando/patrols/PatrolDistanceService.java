package com.cajamarca.sgi.comando.patrols;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class PatrolDistanceService {
    @Inject EntityManager em;
    @ConfigProperty(name="sgi.patrols.distance.max-accuracy-m") Optional<Double> maxAccuracy;
    @ConfigProperty(name="sgi.patrols.distance.max-gap-seconds") Optional<Long> maxGap;

    public Optional<PatrolDistanceCalculator.Policy> policy() {
        if(maxAccuracy.isEmpty() || maxGap.isEmpty()) return Optional.empty();
        if(!Double.isFinite(maxAccuracy.get()) || maxAccuracy.get()<=0 || maxGap.get()<=0)return Optional.empty();
        return Optional.of(new PatrolDistanceCalculator.Policy(maxAccuracy.get(),maxGap.get()));
    }

    /** Se llama dentro de la transacción FINISH: muestras y cierre se confirman juntos. */
    public void complete(UUID tenant,UUID run,JsonNode samples,Instant started,Instant finished) {
        if(samples==null || samples.isNull())return; // Clientes anteriores: no fabricar kilómetros desde hitos sueltos.
        if(!samples.isArray() || samples.size()>10000)throw new BadRequestException("gpsSamples debe ser un arreglo de hasta 10000 muestras");
        Map<Instant,PatrolDistanceCalculator.Sample> unique=new LinkedHashMap<>();
        for(JsonNode item:samples) {
            try {
                Instant at=Instant.parse(item.path("capturedAt").asText());
                if(!item.path("latitude").isNumber() || !item.path("longitude").isNumber())throw new IllegalArgumentException();
                double lat=item.path("latitude").asDouble(),lon=item.path("longitude").asDouble();
                Double accuracy=item.hasNonNull("accuracyMeters")?item.path("accuracyMeters").doubleValue():null;
                Double speed=item.hasNonNull("speedMps")?item.path("speedMps").doubleValue():null;
                if(!Double.isFinite(lat)||Math.abs(lat)>90||!Double.isFinite(lon)||Math.abs(lon)>180
                    ||accuracy==null||!item.path("accuracyMeters").isNumber()||!Double.isFinite(accuracy)||accuracy<0
                    ||speed!=null&&(!item.path("speedMps").isNumber()||!Double.isFinite(speed)||speed<0))throw new IllegalArgumentException();
                var sample=new PatrolDistanceCalculator.Sample(at,lat,lon,accuracy,speed);
                var old=unique.putIfAbsent(at,sample);
                if(old!=null && !old.equals(sample))throw new BadRequestException("Muestras distintas con la misma fecha GPS");
            }catch(BadRequestException e){throw e;}
            catch(RuntimeException e){throw new BadRequestException("Muestra GPS inválida");}
        }
        for(var sample:unique.values()) {
            // Se conserva la hora del GPS. Lecturas cacheadas previas al START no cuentan.
            if(sample.at().isBefore(started)||sample.at().isAfter(finished))continue;
            UUID id=UUID.nameUUIDFromBytes((tenant+":"+run+":"+sample.at()).getBytes(StandardCharsets.UTF_8));
            em.createNativeQuery("""
                insert into patrol_gps_sample(instance_country_id,patrol_execution_id,sample_id,captured_at,latitude,longitude,accuracy_m,speed_mps)
                values(:t,:run,:id,:at,:lat,:lon,:accuracy,:speed) on conflict do nothing
                """).setParameter("t",tenant).setParameter("run",run).setParameter("id",id).setParameter("at",sample.at())
                .setParameter("lat",sample.latitude()).setParameter("lon",sample.longitude())
                .setParameter("accuracy",sample.accuracyM()).setParameter("speed",sample.speedMps()).executeUpdate();
        }
        if(policy().isPresent())measure(tenant,run,policy().orElseThrow());
    }

    /** Permite procesar capturas ya guardadas tras definir la política; nunca modifica un snapshot existente. */
    public int measurePending(UUID tenant,int limit) {
        var configured=policy().orElseThrow(()->new ServiceUnavailableException("Defina precisión GPS y separación máxima antes de publicar PATROL_KM"));
        List<?> runs=em.createNativeQuery("""
            select pe.id from patrol_execution pe where pe.instance_country_id=:t and pe.finished_at is not null
            and exists(select 1 from patrol_gps_sample g where g.instance_country_id=:t and g.patrol_execution_id=pe.id and g.sample_id is not null)
            and not exists(select 1 from patrol_distance_measurement m where m.instance_country_id=:t and m.id=pe.id)
            order by pe.finished_at,pe.id
            """).setParameter("t",tenant).setMaxResults(Math.max(1,Math.min(limit,500))).getResultList();
        int total=0;for(Object id:runs)if(measure(tenant,UUID.fromString(id.toString()),configured))total++;return total;
    }

    private boolean measure(UUID tenant,UUID run,PatrolDistanceCalculator.Policy configured) {
        List<?> executions=em.createNativeQuery("""
            select employee_id,started_at,finished_at from patrol_execution
            where id=:run and instance_country_id=:t and finished_at is not null for update
            """).setParameter("run",run).setParameter("t",tenant).getResultList();
        if(executions.isEmpty())return false;
        if(!em.createNativeQuery("select id from patrol_distance_measurement where id=:run and instance_country_id=:t")
            .setParameter("run",run).setParameter("t",tenant).getResultList().isEmpty())return false;
        Object[] execution=(Object[])executions.getFirst();
        List<?> rows=em.createNativeQuery("""
            select captured_at,latitude,longitude,accuracy_m,speed_mps from patrol_gps_sample
            where instance_country_id=:t and patrol_execution_id=:run and sample_id is not null order by captured_at,id
            """).setParameter("t",tenant).setParameter("run",run).getResultList();
        List<PatrolDistanceCalculator.Sample> samples=new ArrayList<>();
        for(Object raw:rows){Object[] r=(Object[])raw;samples.add(new PatrolDistanceCalculator.Sample(time(r[0]),
            ((Number)r[1]).doubleValue(),((Number)r[2]).doubleValue(),r[3]==null?null:((Number)r[3]).doubleValue(),r[4]==null?null:((Number)r[4]).doubleValue()));}
        var result=PatrolDistanceCalculator.calculate(samples,time(execution[1]),time(execution[2]),configured);
        return em.createNativeQuery("""
            insert into patrol_distance_measurement(id,instance_country_id,employee_id,occurred_at,kilometers,sample_count,
                accepted_segments,rejected_segments,max_accuracy_m,max_gap_seconds)
            values(:run,:t,:employee,:at,:km,:samples,:accepted,:rejected,:accuracy,:gap) on conflict do nothing
            """).setParameter("run",run).setParameter("t",tenant).setParameter("employee",execution[0])
            .setParameter("at",time(execution[2])).setParameter("km",BigDecimal.valueOf(result.meters()/1000).setScale(6,RoundingMode.HALF_UP))
            .setParameter("samples",result.samples()).setParameter("accepted",result.acceptedSegments()).setParameter("rejected",result.rejectedSegments())
            .setParameter("accuracy",configured.maxAccuracyM()).setParameter("gap",configured.maxGapSeconds()).executeUpdate()==1;
    }
    private static Instant time(Object value){
        if(value instanceof java.time.OffsetDateTime t)return t.toInstant();
        if(value instanceof java.sql.Timestamp t)return t.toInstant();
        return Instant.parse(value.toString());
    }
}
