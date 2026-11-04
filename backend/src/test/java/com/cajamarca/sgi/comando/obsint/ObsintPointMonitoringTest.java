package com.cajamarca.sgi.comando.obsint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
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
 * Monitoristas OBSINT del Punto: uno o más de la Compañía del Punto (la asignada en Servicios),
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
    /** Otra Compañía del país, para SEN-02 (cargos altos) y SEN-04 ("Confidencial" para Coordinación). */
    static final UUID OTHER_COMPANY = UUID.fromString("20000000-0000-0000-0000-0000000000f4");
    static final UUID OTHER_MONITOR = UUID.fromString("e0b51000-0000-0000-0000-0000000000d4");
    static final String URL = "/api/obsint/monitoring/points/" + POINT;

    @Inject EntityManager em;

    @BeforeEach
    void employeesAndNoMonitoring() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                insert into company(id,instance_country_id,code,name,status,created_at,updated_at)
                values (:id,:pe,'OBS-PRUEBA-OTRA','Otra Compañía pruebas','ACTIVE',now(),now()) on conflict (id) do nothing""")
                .setParameter("id", OTHER_COMPANY).setParameter("pe", PE).executeUpdate();
            for (Object[] e : new Object[][]{{MONITOR_A, "Monitor A pruebas", "Monitor", GALVARINO}, {MONITOR_B, "Monitor B pruebas", "Agente de Consola de Monitoreo Senior", GALVARINO},
                                              {GUARD, "Guardia pruebas", "Agente de Seguridad", GALVARINO}, {OTHER_MONITOR, "Monitor otra pruebas", "Monitor", OTHER_COMPANY}})
                em.createNativeQuery("""
                    insert into employee_operational_snapshot(id,instance_country_id,employee_id,company_id,full_name,role_code,employment_status,updated_from_source_at,created_at,updated_at)
                    values (gen_random_uuid(),:pe,:employee,:company,:name,:role,'ACTIVE',now(),now(),now())
                    on conflict (instance_country_id,employee_id) do update set company_id=excluded.company_id, role_code=excluded.role_code, employment_status='ACTIVE'""")
                    .setParameter("pe", PE).setParameter("employee", e[0]).setParameter("company", e[3])
                    .setParameter("name", e[1]).setParameter("role", e[2]).executeUpdate();
        });
        clear();
    }

    @AfterEach
    void clear() {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from obsint_point_monitor where instance_country_id=:pe and point_id=:point")
            .setParameter("pe", PE).setParameter("point", UUID.fromString(POINT)).executeUpdate());
    }

    static Map<String, Object> body(UUID... employees) {
        return Map.of("employeeIds", List.of(employees));
    }

    @Test void companyIsTheOneAssignedInServices() {
        as("coord").get(URL).then().statusCode(200)
            .body("companyId", is(GALVARINO.toString())).body("companyName", is("Galvarino")).body("monitors", empty());
        as("coord").get(URL + "/monitors").then().statusCode(200)
            .body("employeeId", hasItems(MONITOR_A.toString(), MONITOR_B.toString())).body("employeeId", not(hasItem(GUARD.toString())));
    }

    @Test void chooseOneOrMoreMonitors() {
        as("coord").contentType(ContentType.JSON).body(body(MONITOR_A, MONITOR_B)).put(URL).then().statusCode(200)
            .body("companyName", is("Galvarino")).body("monitors.name", contains("Monitor A pruebas", "Monitor B pruebas")).body("updatedBy", is("coord"));
        as("coord").contentType(ContentType.JSON).body(body(MONITOR_B)).put(URL).then().statusCode(200).body("monitors.name", contains("Monitor B pruebas"));
        as("coord").contentType(ContentType.JSON).body(body()).put(URL).then().statusCode(200).body("monitors", empty());
    }

    @Test void rejectsEmployeesWithoutMonitoringCargo() {
        as("coord").contentType(ContentType.JSON).body(body(MONITOR_A, GUARD)).put(URL).then().statusCode(400)
            .body("message", containsString("cargo de monitoreo"));
        as("coord").get(URL).then().statusCode(200).body("monitors", empty());
    }

    /** SEN-02/05: cada monitorista llega con su Compañía; para la propia Compañía, sin bandera "Confidencial". */
    @Test void coordinatorListsOnlyOwnCompanyWithCompanyData() {
        as("coord").get(URL + "/monitors").then().statusCode(200)
            .body("employeeId", not(hasItem(OTHER_MONITOR.toString())))
            .body("find { it.employeeId == '" + MONITOR_A + "' }.companyName", is("Galvarino"))
            .body("find { it.employeeId == '" + MONITOR_A + "' }.companyId", is(GALVARINO.toString()))
            .body("confidential", everyItem(is(false)));
    }

    /** SEN-02: un cargo alto ve y asigna monitores de otras Compañías de su alcance. */
    @Test void highRoleListsAndAssignsOtherCompanyMonitors() {
        as("presidente").get(URL + "/monitors").then().statusCode(200)
            .body("employeeId", hasItems(MONITOR_A.toString(), OTHER_MONITOR.toString()))
            .body("find { it.employeeId == '" + OTHER_MONITOR + "' }.companyName", is("Otra Compañía pruebas"))
            .body("confidential", everyItem(is(false)));
        as("presidente").contentType(ContentType.JSON).body(body(MONITOR_A, OTHER_MONITOR)).put(URL).then().statusCode(200)
            .body("monitors.employeeId", hasItems(MONITOR_A.toString(), OTHER_MONITOR.toString()))
            .body("monitors.confidential", everyItem(is(false)));
        // También puede quitarlo.
        as("presidente").contentType(ContentType.JSON).body(body(MONITOR_A)).put(URL).then().statusCode(200)
            .body("monitors.employeeId", contains(MONITOR_A.toString()));
    }

    /** SEN-04: la Coordinación ve como "Confidencial" (bandera) al monitorista de otra Compañía y no puede quitarlo. */
    @Test void coordinatorSeesOtherCompanyMonitorAsConfidentialAndKeepsIt() {
        as("presidente").contentType(ContentType.JSON).body(body(MONITOR_A, OTHER_MONITOR)).put(URL).then().statusCode(200);

        as("coord").get(URL).then().statusCode(200)
            .body("monitors.find { it.employeeId == '" + OTHER_MONITOR + "' }.confidential", is(true))
            .body("monitors.find { it.employeeId == '" + OTHER_MONITOR + "' }.companyName", is("Otra Compañía pruebas"))
            .body("monitors.find { it.employeeId == '" + MONITOR_A + "' }.confidential", is(false));

        as("coord").contentType(ContentType.JSON).body(body(MONITOR_A)).put(URL).then().statusCode(403)
            .body("message", containsString("otra Compañía"));
        // Reenviándolo tal cual (como hace la pantalla), puede cambiar los suyos.
        as("coord").contentType(ContentType.JSON).body(body(MONITOR_B, OTHER_MONITOR)).put(URL).then().statusCode(200)
            .body("monitors.employeeId", containsInAnyOrder(MONITOR_B.toString(), OTHER_MONITOR.toString()));
    }

    /** SEN-02/04: la Coordinación no puede asignar monitores de otra Compañía. */
    @Test void coordinatorCannotAssignOtherCompanyMonitor() {
        as("coord").contentType(ContentType.JSON).body(body(MONITOR_A, OTHER_MONITOR)).put(URL).then().statusCode(403)
            .body("message", containsString("otra Compañía"));
        as("coord").get(URL).then().statusCode(200).body("monitors", empty());
    }

    @Test void monitorCannotConfigure() {
        as("monitor").get(URL).then().statusCode(403);
    }

    /** Quién ve el Punto en la consola, estando de turno. */
    @Test void monitorVisibilityRule() {
        UUID company = UUID.randomUUID(), other = UUID.randomUUID(), me = UUID.randomUUID(), colleague = UUID.randomUUID();
        var none = new ObsintPlatform.Monitoring(company, Set.of());
        var others = new ObsintPlatform.Monitoring(company, Set.of(colleague));
        var mine = new ObsintPlatform.Monitoring(company, Set.of(me, colleague));

        assertTrue(ObsintPlatform.monitorSees(none, me, Set.of(company), Set.of()), "sin monitoristas: cualquier monitor de turno de la Compañía");
        assertFalse(ObsintPlatform.monitorSees(none, me, Set.of(other), Set.of()), "de turno en otra Compañía no lo ve");
        assertFalse(ObsintPlatform.monitorSees(others, me, Set.of(company), Set.of(colleague)), "un monitorista elegido está de turno: solo ellos");
        assertTrue(ObsintPlatform.monitorSees(others, me, Set.of(company), Set.of()), "ningún elegido de turno: cualquiera de la Compañía");
        assertTrue(ObsintPlatform.monitorSees(mine, me, Set.of(company), Set.of(me, colleague)), "uno de varios elegidos lo ve");
        assertTrue(ObsintPlatform.monitorSees(mine, me, Set.of(other), Set.of(me)), "elegido lo ve aunque su turno sea en otro puesto");
        assertFalse(ObsintPlatform.monitorSees(mine, me, Set.of(), Set.of()), "fuera de turno nadie lo ve");
        assertFalse(ObsintPlatform.monitorSees(new ObsintPlatform.Monitoring(null, Set.of()), me, Set.of(company), Set.of()), "Punto aún en Kaibil, sin Compañía");
    }
}
