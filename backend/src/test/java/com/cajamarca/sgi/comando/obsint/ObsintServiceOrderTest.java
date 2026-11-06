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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.*;

/** SEN-01: OBSINT solo se habilita en Puntos con orden de servicio OBSINT: dispositivo OBSINT activo de SICCOM o service.obsint. */
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

    /** Dispositivo OBSINT que SICCOM crea en el Punto (electronic_monitoring_post); devuelve su id. */
    UUID siccomDevice(String status) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                insert into electronic_monitoring_post(id, instance_country_id, point_id, code, source_code2, post_type, name, format,
                    commercial_status, config_status, created_at, updated_at)
                select :id, p.instance_country_id, p.id, :code, :source, 'OBSINT', 'Monitoreo OBSINT prueba', 'MONITOREO', :status, 'TO_CONFIGURE', now(), now()
                from point p where p.id=:point""")
            .setParameter("id", id).setParameter("code", "TEST-SE-" + suffix).setParameter("source", "SE-TEST-" + suffix)
            .setParameter("status", status).setParameter("point", POINT).executeUpdate());
        return id;
    }

    void deviceStatus(UUID device, String status) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update electronic_monitoring_post set commercial_status=:s where id=:id")
            .setParameter("s", status).setParameter("id", device).executeUpdate());
    }

    String deviceConfig(UUID device) {
        return QuarkusTransaction.requiringNew().call(() -> (String) em.createNativeQuery("select config_status from electronic_monitoring_post where id=:id")
            .setParameter("id", device).getSingleResult());
    }

    @Test void siccomDeviceEnablesObsintUntilInactivated() {
        UUID device = siccomDevice("ACTIVE");
        try {
            as("coord").get(CONFIG + "/summary").then().statusCode(200).body("enabled", is(true));
            as("coord").get(CONFIG + "/protocols").then().statusCode(200);
            deviceStatus(device, "INACTIVE");
            as("coord").get(CONFIG + "/summary").then().statusCode(200).body("enabled", is(false));
            as("coord").get(CONFIG + "/protocols").then().statusCode(403).body("code", is("OBSINT_SIN_ORDEN"));
        } finally {
            deviceStatus(device, "INACTIVE");
        }
    }

    @Test void activeProtocolMarksTheDeviceConfigured() {
        UUID device = siccomDevice("ACTIVE");
        try {
            ObsintResourceTest.activeProtocolWithConsigna();
            assertEquals("CONFIGURED", deviceConfig(device));
            String active = as("coord").get(CONFIG + "/protocols").then().statusCode(200).extract().path("find { it.status == 'ACTIVO' }.id");
            as("coord").post("/api/obsint/config/protocols/" + active + "/deactivate").then().statusCode(200);
            assertEquals("TO_CONFIGURE", deviceConfig(device));
            as("coord").post("/api/obsint/config/protocols/" + active + "/activate").then().statusCode(200);
            assertEquals("CONFIGURED", deviceConfig(device));
        } finally {
            deviceStatus(device, "INACTIVE");
        }
    }

    /** Una consigna con alcance en el dispositivo OBSINT cuenta como OBSINT aunque no tenga la casilla. */
    @Test void consignmentScopedToTheDeviceIsObsint() {
        UUID device = siccomDevice("ACTIVE");
        try {
            // Nueva versión del protocolo de Consignas activo (conserva sus consignas, que usan otras pruebas) o uno nuevo.
            String current = QuarkusTransaction.requiringNew().call(() -> {
                List<?> rows = em.createNativeQuery("select cast(id as varchar) from consignment_protocol where point_id=:point and status='ACTIVO'")
                    .setParameter("point", POINT).getResultList();
                return rows.isEmpty() ? null : rows.get(0).toString();
            });
            String protocolId = current != null
                ? as("coord").post("/api/consignments/protocols/" + current + "/fork").then().statusCode(200).extract().path("id")
                : as("coord").contentType(ContentType.JSON).body(Map.of("pointId", POINT, "name", "Consignas dispositivo " + UUID.randomUUID()))
                    .post("/api/consignments/protocols").then().statusCode(200).extract().path("id");
            var item = as("coord").contentType(ContentType.JSON).body(Map.of("title", "Portón cerrado"))
                .post("/api/consignments/protocols/" + protocolId + "/items").then().statusCode(200).extract();
            as("coord").contentType(ContentType.JSON).body(Map.of("title", "Portón cerrado", "instruction", "El portón permanece cerrado.",
                    "scopeType", "POSTS", "postIds", List.of(), "electronicDeviceIds", List.of(device.toString()), "obsint", false))
                .put("/api/consignments/items/" + item.path("id")).then().statusCode(200).body("obsint", is(false));
            as("coord").post("/api/consignments/protocols/" + protocolId + "/publish").then().statusCode(200);
            as("coord").post("/api/consignments/protocols/" + protocolId + "/activate").then().statusCode(200);
            String code = item.path("code");
            as("coord").get(CONFIG + "/consignments").then().statusCode(200).body("code", hasItem(code));
        } finally {
            deviceStatus(device, "INACTIVE");
        }
    }

    /** Opción A: cada cámara se asigna a su dispositivo de SIC:COM; la tarea OBSINT solo acepta las cámaras del dispositivo de su consigna. */
    @Test void camerasBelongToTheirSiccomDevice() {
        UUID portal = siccomDevice("ACTIVE"), yard = siccomDevice("ACTIVE");
        try {
            String cam = as("coord").contentType(ContentType.JSON).body(Map.of("family", "VIDEO", "componentType", "Cámara Bala", "name", "Cámara portón",
                    "ip", "192.168.1.20", "port", 554, "username", "admin", "password", "secreta", "electronicDeviceId", portal.toString()))
                .post("/api/secelec/points/" + POINT + "/components").then().statusCode(200)
                .body("electronicDeviceId", is(portal.toString())).extract().path("id");
            as("coord").contentType(ContentType.JSON).body(Map.of("family", "VIDEO", "componentType", "Cámara Bala", "name", "Dispositivo ajeno",
                    "ip", "192.168.1.21", "port", 554, "username", "admin", "password", "secreta", "electronicDeviceId", UUID.randomUUID().toString()))
                .post("/api/secelec/points/" + POINT + "/components").then().statusCode(400);
            as("coord").get("/api/secelec/points/" + POINT + "/devices").then().statusCode(200)
                .body("find { it.id == '" + portal + "' }.cameras", is(1)).body("find { it.id == '" + yard + "' }.cameras", is(0));
            as("coord").get(CONFIG + "/cameras").then().statusCode(200).body("find { it.id == '" + cam + "' }.deviceId", is(portal.toString()));

            // Consigna del patio: su sugerencia trae ese dispositivo y la cámara del portón se rechaza.
            String code = consignmentForDevice(yard, "Patio sin personas");
            as("coord").get(CONFIG + "/schedule-suggestion?consignmentCode=" + code).then().statusCode(200).body("deviceIds", contains(yard.toString()));
            String draft = as("coord").post(CONFIG + "/protocols/draft").then().statusCode(200).extract().path("id");
            Map<String, Object> task = new java.util.HashMap<>(ObsintResourceTest.consigna("ignorado", List.of(cam), ObsintResourceTest.ALL_DAY));
            task.put("consignmentCode", code);
            as("coord").contentType(ContentType.JSON).body(task).post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(400)
                .body("message", containsString("pertenece a otro dispositivo"));
        } finally {
            deviceStatus(portal, "INACTIVE");
            deviceStatus(yard, "INACTIVE");
        }
    }

    String consignmentForDevice(UUID device, String title) { return consignmentForDeviceList(List.of(device), title); }

    /** Nueva versión del protocolo de Consignas activo con una consigna cuyo alcance son esos dispositivos; devuelve su código. */
    String consignmentForDeviceList(List<UUID> devices, String title) {
        String current = QuarkusTransaction.requiringNew().call(() -> {
            List<?> rows = em.createNativeQuery("select cast(id as varchar) from consignment_protocol where point_id=:point and status='ACTIVO'")
                .setParameter("point", POINT).getResultList();
            return rows.isEmpty() ? null : rows.get(0).toString();
        });
        String protocolId = current != null
            ? as("coord").post("/api/consignments/protocols/" + current + "/fork").then().statusCode(200).extract().path("id")
            : as("coord").contentType(ContentType.JSON).body(Map.of("pointId", POINT, "name", "Consignas dispositivo " + UUID.randomUUID()))
                .post("/api/consignments/protocols").then().statusCode(200).extract().path("id");
        var item = as("coord").contentType(ContentType.JSON).body(Map.of("title", title))
            .post("/api/consignments/protocols/" + protocolId + "/items").then().statusCode(200).extract();
        as("coord").contentType(ContentType.JSON).body(Map.of("title", title, "instruction", title + ".", "scopeType", "POSTS", "postIds", List.of(),
                "electronicDeviceIds", devices.stream().map(UUID::toString).toList(), "obsint", true))
            .put("/api/consignments/items/" + item.path("id")).then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + protocolId + "/publish").then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + protocolId + "/activate").then().statusCode(200);
        return item.path("code");
    }

    void shift(UUID device, String start, String end, int dayMask) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                insert into electronic_monitoring_shift(id, instance_country_id, monitoring_post_id, shift_code, shift_name, start_time, end_time, day_mask, active, created_at, updated_at)
                select :id, d.instance_country_id, d.id, 'OBS24', 'Monitoreo OBSINT', cast(:start as time), cast(:end as time), :mask, true, now(), now()
                from electronic_monitoring_post d where d.id=:device""")
            .setParameter("id", UUID.randomUUID()).setParameter("device", device).setParameter("start", start).setParameter("end", end)
            .setParameter("mask", dayMask).executeUpdate());
    }

    /** Caso Norte (SICCOM): una consigna con varios dispositivos del mismo horario sugiere ese horario; con horarios distintos, no. */
    @Test void scheduleSuggestionWithSeveralDevices() {
        UUID a = siccomDevice("ACTIVE"), b = siccomDevice("ACTIVE"), c = siccomDevice("ACTIVE");
        try {
            shift(a, "01:30", "18:30", 42); shift(b, "01:30", "18:30", 42); shift(c, "09:00", "21:00", 85);
            String same = consignmentForDeviceList(List.of(a, b), "Norte acceso");
            as("coord").get(CONFIG + "/schedule-suggestion?consignmentCode=" + same).then().statusCode(200)
                .body("available", is(true)).body("slots.dayOfWeek", contains(2, 4, 6)).body("slots[0].start", is("01:30")).body("deviceIds", hasSize(2));
            String mixed = consignmentForDeviceList(List.of(a, c), "Sur mezcla");
            as("coord").get(CONFIG + "/schedule-suggestion?consignmentCode=" + mixed).then().statusCode(200)
                .body("available", is(false)).body("message", containsString("horarios distintos"));
        } finally {
            deviceStatus(a, "INACTIVE"); deviceStatus(b, "INACTIVE"); deviceStatus(c, "INACTIVE");
        }
    }
}
