package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.socket.SocketConfig;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ObsintSocketDisabledTest {
    @Inject EntityManager em;
    @Inject SocketConfig config;
    @Inject Event<ObsintMessageQueued> queued;
    @Inject ObsintAgentRecipients recipients;
    @Test void apagadoConservaModoPrueba() {
        assertFalse(config.enabled());
        io.restassured.RestAssured.given().contentType("application/json").body(java.util.Map.of(
            "messageId",UUID.randomUUID(),"channel","persona.9428","status","READ"))
            .post("/api/internal/socket/acks").then().statusCode(404);
        QuarkusTransaction.requiringNew().run(()->{
            var fixture=ObsintSocketFixtures.create(em);
            var message=fixture.send(em,config,queued,true);
            em.flush();
            assertEquals("TEST",message.deliveryStatus);
            assertEquals(0,ObsintAgentMessageRecipient.count("messageId",message.id));
        });
    }
    @Test void resuelveSoloTurnoActualDeduplicandoEmpleadoEfectivo() {
        QuarkusTransaction.requiringNew().run(()->{
            // Tablas temporales de la conexión: prueban el SQL real sin depender de catálogos UAT.
            em.createNativeQuery("create temporary table operational_assignment(id uuid,instance_country_id uuid,shift_occurrence_id uuid,employee_id uuid,actual_employee_id uuid,status text) on commit drop").executeUpdate();
            em.createNativeQuery("create temporary table shift_occurrence(id uuid,instance_country_id uuid,post_id uuid,starts_at timestamptz,ends_at timestamptz) on commit drop").executeUpdate();
            em.createNativeQuery("create temporary table employee_operational_snapshot(id uuid,instance_country_id uuid,employee_id uuid,persona_id bigint,full_name text,updated_at timestamptz) on commit drop").executeUpdate();
            em.createNativeQuery("create temporary table post(id uuid,instance_country_id uuid,point_id uuid,name text) on commit drop").executeUpdate();
            UUID tenant=UUID.randomUUID(),point=UUID.randomUUID(),employee=UUID.randomUUID(),replacement=UUID.randomUUID();
            Instant now=Instant.now();
            for(int i=0;i<6;i++) {
                UUID post=UUID.randomUUID(),shift=UUID.randomUUID();
                UUID scoped=i==5 ? UUID.randomUUID() : tenant;
                em.createNativeQuery("insert into post values(:id,:tenant,:point,'Puesto temporal')").setParameter("id",post).setParameter("tenant",scoped).setParameter("point",point).executeUpdate();
                em.createNativeQuery("insert into shift_occurrence values(:id,:tenant,:post,:start,:end)")
                    .setParameter("id",shift).setParameter("tenant",scoped).setParameter("post",post)
                    .setParameter("start",i==3 ? now.plusSeconds(60) : now.minusSeconds(60))
                    .setParameter("end",i==4 ? now : now.plusSeconds(3600)).executeUpdate();
                em.createNativeQuery("insert into operational_assignment values(:id,:tenant,:shift,:employee,:actual,:status)")
                    .setParameter("id",UUID.randomUUID()).setParameter("tenant",scoped).setParameter("shift",shift)
                    .setParameter("employee",employee).setParameter("actual",replacement).setParameter("status",i==2 ? "REMOVED" : "PUBLISHED").executeUpdate();
            }
            var result=recipients.onShift(tenant,point,now);
            assertEquals(1,result.size()); assertEquals(replacement,result.getFirst().employeeId());
        });
    }
}
