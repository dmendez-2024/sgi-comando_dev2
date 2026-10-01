package com.cajamarca.sgi.comando.common;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class FeaturesResourceTest {
    @Test void exposesUatFlagsToAuthenticatedUsers() {
        as("coord").get("/api/features").then().statusCode(200)
            .body("uatTools", is(true)).body("visintMode", is("MOCK")).body("visintSimulated", is(true));
    }

    @Test void requiresAuthentication() {
        given().get("/api/features").then().statusCode(401);
    }
}
