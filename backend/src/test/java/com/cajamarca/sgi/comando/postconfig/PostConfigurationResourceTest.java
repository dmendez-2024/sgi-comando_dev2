package com.cajamarca.sgi.comando.postconfig;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.cajamarca.sgi.comando.operator.OperatorFixtures.POST_GGTT01;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PostConfigurationResourceTest {
    private static final String POINT="40000000-0000-0000-0000-000000000001";

    @Test
    void listIncludesPostCodeAndNameEvenWithoutAlias(){
        as("coord").queryParam("pointId",POINT).get("/api/post-configurations").then().statusCode(200)
            .body("find{it.postId=='"+POST_GGTT01+"'}.postCode",is("GGTT01"))
            .body("find{it.postId=='"+POST_GGTT01+"'}.postName",is("Control de Acceso Principal"))
            .body("postName",everyItem(not(blankOrNullString())));
    }
}
