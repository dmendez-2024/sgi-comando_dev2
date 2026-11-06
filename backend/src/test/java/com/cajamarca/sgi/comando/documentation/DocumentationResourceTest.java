package com.cajamarca.sgi.comando.documentation;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.instanceOf;

@QuarkusTest
class DocumentationResourceTest {
    @Test
    void authorizedUserCanListPersistedDocuments(){
        as("coord").get("/api/documents").then().statusCode(200).body("$",instanceOf(java.util.List.class));
    }

    @Test
    void onlyAuthorizedLeadershipCanUpload(){
        as("coord").multiPart("name","Reglamento").multiPart("version","v1")
            .multiPart("status","VIGENTE").post("/api/documents").then().statusCode(403);
    }

    @Test
    void uploadRequiresPdf(){
        as("presidente").multiPart("name","Reglamento").multiPart("version","v1")
            .multiPart("status","VIGENTE").post("/api/documents").then().statusCode(400);
    }
}
