package com.cajamarca.sgi.comando.obsint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Monitorista OBSINT del Punto: responsable y suplente de la Compañía del Punto (la asignada en Servicios),
 * y quién lo ve en la consola según eso.
 */
@QuarkusTest
class ObsintPointMonitoringTest {
    static final UUID PE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final String POINT = "40000000-0000-0000-0000-000000000001";
    static final UUID GALVARINO = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID MONITOR_A = UUID.fromString("e0b51000-0000-0000-0000-0000000000a1");
    static final UUID MONITOR_B = UUID.fromString("e0b51000-0000-0000-0000-0000000000b2");
    static final UUID GUARD = UUID.fromString("e0b51000-0000-0000-0000-0000000000c3");
    static final String URL = "/api/obsint/monitoring/points/" + POINT;

    @Inject EntityManager em;

    @BeforeEach
    void employeesAndNoMonitoring() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (Object[] e : new Object[][]{{MONITOR_A, "Monitor A pruebas", "Monitor"}, {MONITOR_B, "Monitor B pruebas", "Agente de Consola de Monitoreo Senior"},
                                              {GUARD, "Guardia pruebas", "Agente de Seguridad"}})
                em.createNativeQuery("""
                    insert into employee_operational_snapshot(id,instance_country_id,employee_id,company_id,full_name,role_code,employment_status,updated_from_source_at,created_at,updated_at)
                    values (gen_random_uuid(),:pe,:employee,:company,:name,:role,'ACTIVE',now(),now(),now())
                    on conflict (instance_country_id,employee_id) do update set company_id=excluded.company_id, role_code=excluded.role_code, employment_status='ACTIVE'""")
                    .setParameter("pe", PE).setParameter("employee", e[0]).setParameter("company", GALVARINO)
                    .setParameter("name", e[1]).setParameter("role", e[2]).executeUpdate();
        });
        clear();
    }

    @AfterEach
    void clear() {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from obsint_point_monitoring where instance_country_id=:pe and point_id=:point")
            .setParameter("pe", PE).setParameter("point", UUID.fromString(POINT)).executeUpdate());
    }

    static Map<String, Object> body(UUID responsible, UUID backup) {
        Map<String, Object> b = new HashMap<>();
        b.put("responsibleEmployeeId", responsible); b.put("backupEmployeeId", backup);
        return b;
    }

    @Test void companyIsTheOneAssignedInServices() {
        as("coord").get(URL).then().statusCode(200)
            .body("companyId", is(GALVARINO.toString())).body("companyName", is("Galvarino")).body("configured", is(false)).body("responsible", nullValue());
        as("coord").get(URL + "/monitors").then().statusCode(200)
            .body("employeeId", hasItems(MONITOR_A.toString(), MONITOR_B.toString())).body("employeeId", not(hasItem(GUARD.toString())));
    }

    @Test void chooseResponsibleAndBackup() {
        as("coord").contentType(ContentType.JSON).body(body(MONITOR_A, MONITOR_B)).put(URL).then().statusCode(200)
            .body("companyName", is("Galvarino")).body("configured", is(true))
            .body("responsible.name", is("Monitor A pruebas")).body("backup.name", is("Monitor B pruebas")).body("updatedBy", is("coord"));
        as("coord").get(URL).then().statusCode(200).body("responsible.employeeId", is(MONITOR_A.toString()));
        as("coord").contentType(ContentType.JSON).body(body(null, null)).put(URL).then().statusCode(200).body("configured", is(false));
    }

    @Test void rejectsInvalidSelections() {
        as("coord").contentType(ContentType.JSON).body(body(GUARD, null)).put(URL).then().statusCode(400)
            .body("message", containsString("cargo de monitoreo"));
        as("coord").contentType(ContentType.JSON).body(body(MONITOR_A, MONITOR_A)).put(URL).then().statusCode(400)
            .body("message", containsString("otra persona"));
        as("coord").contentType(ContentType.JSON).body(body(null, MONITOR_B)).put(URL).then().statusCode(400)
            .body("message", containsString("responsable"));
    }

    @Test void monitorCannotConfigure() {
        as("monitor").get(URL).then().statusCode(403);
    }

    /** Quién ve el Punto en la consola, estando de turno. */
    @Test void monitorVisibilityRule() {
        UUID company = UUID.randomUUID(), other = UUID.randomUUID(), me = UUID.randomUUID(), colleague = UUID.randomUUID();
        var unassigned = new ObsintPlatform.Monitoring(company, null, null);
        var assigned = new ObsintPlatform.Monitoring(company, colleague, null);
        var mine = new ObsintPlatform.Monitoring(company, me, null);

        assertTrue(ObsintPlatform.monitorSees(unassigned, me, Set.of(company), Set.of()), "sin responsable: cualquier monitor de turno de la Compañía");
        assertFalse(ObsintPlatform.monitorSees(unassigned, me, Set.of(other), Set.of()), "de turno en otra Compañía no lo ve");
        assertFalse(ObsintPlatform.monitorSees(assigned, me, Set.of(company), Set.of(colleague)), "el responsable está de turno: solo él");
        assertTrue(ObsintPlatform.monitorSees(assigned, me, Set.of(company), Set.of()), "el responsable no está de turno: cualquiera de la Compañía");
        assertTrue(ObsintPlatform.monitorSees(mine, me, Set.of(other), Set.of(me)), "el responsable lo ve aunque su turno sea en otro puesto");
        assertFalse(ObsintPlatform.monitorSees(mine, me, Set.of(), Set.of()), "fuera de turno nadie lo ve");
        assertFalse(ObsintPlatform.monitorSees(new ObsintPlatform.Monitoring(null, null, null), me, Set.of(company), Set.of()), "Punto aún en Kaibil, sin Compañía");
    }
}
