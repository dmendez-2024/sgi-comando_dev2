package com.cajamarca.sgi.comando.obsint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

/** SEN-01: OBSINT solo se habilita en Puntos cuyo servicio es una orden de servicio OBSINT (service.obsint). */
@QuarkusTest
class ObsintServiceOrderTest {
    static final UUID POINT = UUID.fromString("40000000-0000-0000-0000-000000000001");
    static final String CONFIG = "/api/obsint/config/points/" + POINT;
    static final String MONITORING = "/api/obsint/monitoring/points/" + POINT;

    @Inject EntityManager em;

    void obsintOrder(boolean on) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "update service set obsint=:on where id=(select service_id from point where id=:point)")
            .setParameter("on", on).setParameter("point", POINT).executeUpdate());
    }

    @BeforeEach void withoutOrder() { obsintOrder(false); }
    @AfterEach void restore() { obsintOrder(true); }

    @Test void summaryReportsDisabledWithoutError() {
        as("coord").get(CONFIG + "/summary").then().statusCode(200)
            .body("enabled", is(false)).body("status", is("blocked")).body("meta", is("Requiere orden de servicio OBSINT"));
        obsintOrder(true);
        as("coord").get(CONFIG + "/summary").then().statusCode(200).body("enabled", is(true));
    }

    @Test void configurationAndMonitoringAreForbiddenWithCode() {
        as("coord").get(CONFIG + "/protocols").then().statusCode(403)
            .body("code", is("OBSINT_SIN_ORDEN")).body("message", is("Requiere orden de servicio OBSINT"));
        as("coord").post(CONFIG + "/protocols/draft").then().statusCode(403).body("code", is("OBSINT_SIN_ORDEN"));
        as("coord").get(CONFIG + "/cameras").then().statusCode(403).body("code", is("OBSINT_SIN_ORDEN"));
        as("coord").get(MONITORING).then().statusCode(403).body("code", is("OBSINT_SIN_ORDEN"));
        as("coord").contentType(ContentType.JSON).body(Map.of("employeeIds", List.of())).put(MONITORING).then().statusCode(403)
            .body("code", is("OBSINT_SIN_ORDEN"));
    }

    @Test void otherErrorsKeepTheirShape() {
        obsintOrder(true);
        as("coord").get("/api/obsint/config/points/" + UUID.randomUUID() + "/summary").then().statusCode(404)
            .body("message", notNullValue()).body("code", nullValue());
    }

    @Test void consoleAccessFollowsTheOrder() {
        // Con todos los Puntos del tenant sin orden (en la base de pruebas solo los del servicio de prueba la tienen) no hay acceso.
        ObsintMonitorShift.monitorUserOnShift();
        as("monitor").get("/api/obsint/access").then().statusCode(200).body("enabled", is(false));
        obsintOrder(true);
        as("monitor").get("/api/obsint/access").then().statusCode(200).body("enabled", is(true));
    }
}
