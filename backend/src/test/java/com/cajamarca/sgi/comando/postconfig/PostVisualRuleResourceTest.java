package com.cajamarca.sgi.comando.postconfig;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static com.cajamarca.sgi.comando.operator.OperatorFixtures.POST_GGTT01;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PostVisualRuleResourceTest {
    private static final String PATH="/api/post-configurations/"+POST_GGTT01+"/visual-rule";

    @Test
    void onlyDirectorsCanChangeTheRuleAndChangesAreHistorical(){
        Map<String,Object> body=Map.of("thresholdValue",0.78,"historicalPresentationMonths",12);
        as("coord").contentType(ContentType.JSON).body(body).put(PATH).then().statusCode(403);
        as("presidente").contentType(ContentType.JSON).body(body).put(PATH).then().statusCode(200)
            .body("thresholdValue",is(0.78f)).body("referenceImageCount",nullValue()).body("effectiveReferenceImageCount",greaterThanOrEqualTo(0));
        as("coord").get(PATH).then().statusCode(200).body("historicalPresentationMonths",is(12));
        as("presidente").get(PATH+"/history").then().statusCode(200).body("size()",greaterThanOrEqualTo(1));
        as("coord").get(PATH+"/history").then().statusCode(403);
    }

    @Test
    void rejectsOutOfRangeValues(){
        as("presidente").contentType(ContentType.JSON).body(Map.of("thresholdValue",1.01))
            .put(PATH).then().statusCode(400);
        as("presidente").contentType(ContentType.JSON).body(Map.of("thresholdValue",-0.01))
            .put(PATH).then().statusCode(400);
        as("presidente").contentType(ContentType.JSON).body(Map.of("referenceImageCount",4))
            .put(PATH).then().statusCode(400);
        as("presidente").contentType(ContentType.JSON).body(Map.of("historicalPresentationMonths",0))
            .put(PATH).then().statusCode(400);
        as("presidente").contentType(ContentType.JSON).body(Map.of("historicalPresentationMonths",121))
            .put(PATH).then().statusCode(400);
    }
}
