package com.cajamarca.sgi.comando.bitacora;

import com.cajamarca.sgi.comando.operator.TaskFixtures;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.POST_GGTT01;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LogbookProtocolExportTest {
    private static final Path PHOTO=Path.of("src/test/resources/fixtures/sample.jpg");

    @Test void onlyPublishedVersionsCanBeExported() {
        String protocolId=TaskFixtures.draftLogbook().get("protocolId").toString();
        as("coord").contentType("application/json").body("{\"scope\":\"COMPANY\"}")
            .post("/api/bitacora/protocols/"+protocolId+"/exports").then().statusCode(409);
    }

    @Test void importCreatesIndependentDraftWithStandardImages() {
        String sourceId=TaskFixtures.activeLogbook(List.of(PHOTO)).get("protocolId").toString();
        String exportId=as("coord").contentType("application/json").body("{\"scope\":\"COMPANY\"}")
            .post("/api/bitacora/protocols/"+sourceId+"/exports").then().statusCode(200).extract().path("id");
        String path="/api/bitacora/protocol-library";
        as("coord").queryParam("postId",POST_GGTT01).get(path).then().statusCode(200)
            .body("find { it.id == '"+exportId+"' }.scope",is("COMPANY"));
        var imported=as("coord").contentType("application/json")
            .body("{\"postId\":\""+POST_GGTT01+"\"}")
            .post(path+"/"+exportId+"/import").then().statusCode(200)
            .body("status",is("BORRADOR"),"versionNo",is(1),"sourceModelType",is("COMPANY"),
                "basedOnProtocolId",is(sourceId),"applicablePostIds",hasItem(POST_GGTT01.toString()),
                "accreditations[0].fields.find { it.name == 'Cédula' }.standardImages.size()",is(1))
            .extract();
        String importedId=imported.path("id");
        String importedCode=imported.path("code");
        String importedSeries=imported.path("seriesId");
        org.junit.jupiter.api.Assertions.assertNotEquals(sourceId,importedId);
        org.junit.jupiter.api.Assertions.assertNotEquals(sourceId,importedSeries);
        as("coord").get("/api/bitacora/protocols/"+sourceId+"/history").then().statusCode(200)
            .body("find { it.id == '"+sourceId+"' }.status",is("ACTIVO"));
        org.junit.jupiter.api.Assertions.assertTrue(importedCode.startsWith("PRO-BA-"));
    }

    @Test void companyExportIsIdempotentAndCountryExportRequiresDirector() {
        String protocolId=TaskFixtures.activeLogbook(List.of(PHOTO)).get("protocolId").toString();
        String path="/api/bitacora/protocols/"+protocolId+"/exports";
        as("coord").contentType("application/json").body("{\"scope\":\"COMPANY\"}")
            .post(path).then().statusCode(200).body("scope",is("COMPANY"),"alreadyExported",is(false));
        as("coord").contentType("application/json").body("{\"scope\":\"COMPANY\"}")
            .post(path).then().statusCode(200).body("scope",is("COMPANY"),"alreadyExported",is(true));
        as("coord").contentType("application/json").body("{\"scope\":\"COUNTRY\"}")
            .post(path).then().statusCode(403);
        as("dnacional").contentType("application/json").body("{\"scope\":\"COUNTRY\"}")
            .post(path).then().statusCode(200).body("scope",is("COUNTRY"),"alreadyExported",is(false));
        as("coord").get(path).then().statusCode(200).body("size()",is(2));
    }
}