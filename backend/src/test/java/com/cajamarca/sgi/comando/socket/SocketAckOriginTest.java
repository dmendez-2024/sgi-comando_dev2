package com.cajamarca.sgi.comando.socket;

import io.quarkus.test.junit.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(SocketAckOriginTest.Profile.class)
class SocketAckOriginTest {
    public static class Profile implements QuarkusTestProfile {
        public Map<String,String> getConfigOverrides() { return Map.of("sgi.socket.enabled","true",
            "sgi.socket.url","http://localhost:1/no-disponible","sgi.socket.ack.allowed-ips","192.0.2.0/24",
            "sgi.socket.ack.token","ack-test","sgi.smc.enabled","false"); }
    }
    @Test void rechazaIpRemotaAunqueXForwardedForDigaOtraCosa() {
        given().header("X-Socket-Ack-Token","ack-test").header("X-Forwarded-For","192.0.2.1")
            .contentType("application/json").body(Map.of("messageId",UUID.randomUUID(),"channel","persona.9428","status","READ"))
            .post("/api/internal/socket/acks").then().statusCode(403);
    }
}
