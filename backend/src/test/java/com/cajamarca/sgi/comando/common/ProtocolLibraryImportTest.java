package com.cajamarca.sgi.comando.common;

import com.cajamarca.sgi.comando.operator.TaskFixtures;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProtocolLibraryImportTest {
    @Test void patrolImportCopiesRulesAndPhotoIntoIndependentDraft() {
        String source=publishPatrolWithCheckpoint(-2.17,-79.92).get("protocolId").toString();
        String path="/api/patrols/protocols/"+source+"/exports";
        as("coord").contentType("application/json").body("{\"scope\":\"COUNTRY\"}").post(path).then().statusCode(403);
        String exportId=as("coord").contentType("application/json").body("{\"scope\":\"COMPANY\"}")
            .post(path).then().statusCode(200).body("alreadyExported",is(false)).extract().path("id");
        as("coord").queryParam("postId",POST_GGTT01).get("/api/patrols/protocol-library").then().statusCode(200)
            .body("find { it.id == '"+exportId+"' }.itemCount",is(1));
        var imported=as("coord").contentType("application/json").body("{\"postId\":\""+POST_GGTT01+"\"}")
            .post("/api/patrols/protocol-library/"+exportId+"/import").then().statusCode(200)
            .body("status",is("BORRADOR"),"versionNo",is(1),"basedOnProtocolId",is(source),
                "patrols[0].checkpoints[0].standardImages.size()",is(1),
                "patrols[0].checkpoints[0].rules.size()",greaterThan(0)).extract();
        assertNotEquals(source,imported.path("id"));
        assertNotEquals(source,imported.path("seriesId"));
    }

    @Test void consignmentImportCopiesEvidenceAndPhotoIntoIndependentDraft() {
        String source=TaskFixtures.activeConsignment(List.of(Path.of("src/test/resources/fixtures/sample.jpg"))).get("protocolId").toString();
        String exportId=as("coord").contentType("application/json").body("{\"scope\":\"COMPANY\"}")
            .post("/api/consignments/protocols/"+source+"/exports").then().statusCode(200).extract().path("id");
        var imported=as("coord").contentType("application/json").body("{\"postId\":\""+POST_GGTT01+"\"}")
            .post("/api/consignments/protocol-library/"+exportId+"/import").then().statusCode(200)
            .body("status",is("BORRADOR"),"versionNo",is(1),"basedOnProtocolId",is(source),
                "consignments[0].evidences[0].standardImages.size()",is(1)).extract();
        assertNotEquals(source,imported.path("id"));
        assertNotEquals(source,imported.path("seriesId"));
    }
}
