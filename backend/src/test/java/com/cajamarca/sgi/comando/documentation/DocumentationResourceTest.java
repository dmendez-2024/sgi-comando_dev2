package com.cajamarca.sgi.comando.documentation;

import com.cajamarca.sgi.comando.security.TestIdentTokenVerifier;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.instanceOf;

@QuarkusTest
class DocumentationResourceTest {
    @Test
    void nonPresidentRolesCanListPersistedDocuments(){
        asRole("GERENCIA_GENERAL").get("/api/documents").then().statusCode(200).body("$",instanceOf(java.util.List.class));
        asRole("COORDINADOR_COMPANIA").get("/api/documents").then().statusCode(200).body("$",instanceOf(java.util.List.class));
    }

    @Test
    void onlyPresidentCanUpload(){
        asRole("GERENCIA_GENERAL").multiPart("name","Reglamento").multiPart("version","v1")
            .multiPart("status","VIGENTE").post("/api/documents").then().statusCode(403);
        asRole("COORDINADOR_COMPANIA").multiPart("name","Reglamento").multiPart("version","v1")
            .multiPart("status","VIGENTE").post("/api/documents").then().statusCode(403);
    }

    @Test
    void uploadRequiresPdf(){
        asRole("PRESIDENTE").multiPart("name","Reglamento").multiPart("version","v1")
            .multiPart("status","VIGENTE").post("/api/documents").then().statusCode(400);
    }

    @Test
    void onlyPresidentCanEditStatus(){
        String path="/api/documents/"+UUID.randomUUID()+"/status";
        Map<String,String> body=Map.of("status","NO_VIGENTE");
        asRole("GERENCIA_GENERAL").contentType(ContentType.JSON).body(body).patch(path).then().statusCode(403);
        asRole("PRESIDENTE").contentType(ContentType.JSON).body(body).patch(path).then().statusCode(404);
    }

    private static RequestSpecification asRole(String role){
        Map<String,Object> claims=Map.of(
            "sub","ident-"+role.toLowerCase(),
            "aud","SGI_COM",
            "token_use","access",
            "exp",Instant.now().plusSeconds(600).getEpochSecond(),
            "groups",List.of(role),
            "active_instance_country_id","11111111-1111-1111-1111-111111111111",
            "email",role.toLowerCase()+"@cajamarca.test"
        );
        return given().header("Authorization","Bearer "+TestIdentTokenVerifier.token(claims));
    }
}
