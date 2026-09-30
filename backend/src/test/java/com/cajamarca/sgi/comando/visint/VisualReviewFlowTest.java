package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.operator.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class VisualReviewFlowTest {
    @Inject EntityManager em;
    @Inject VisualReviewWorker worker;
    @Inject MockVisintAdapter mock;
    static final Path SAME = Path.of("src/test/resources/fixtures/sample.jpg"), OTHER = Path.of("src/test/resources/fixtures/sample2.jpg");

    /** Otras clases de prueba dejan revisiones en cola: se procesan antes para que forceNextResult/failNextCalls apliquen a la de esta prueba. */
    @BeforeEach void drainQueue() { worker.processDue(); }

    Object[] review(UUID event) {
        return (Object[]) em.createNativeQuery("select status, result, attempts, simulated, findings, last_error from visual_review where task_execution_id=:e")
            .setParameter("e", event).getSingleResult();
    }
    void makeDue(UUID event) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update visual_review set next_attempt_at=now() where task_execution_id=:e").setParameter("e", event).executeUpdate());
    }

    @Test void queuedThenVerdictFromVisint() throws Exception {
        Map<String,Object> ids = VisintFixtures.publishVisintPatrol(List.of(OTHER, SAME));
        UUID event = VisintFixtures.executeWithPhoto(em, ids, SAME);
        assertEquals("QUEUED_FOR_VISINT", review(event)[0]);
        assertEquals(2, ((Number) em.createNativeQuery("select count(*) from visual_review_standard s join visual_review r on r.id=s.review_id where r.task_execution_id=:e")
            .setParameter("e", event).getSingleResult()).intValue(), "la revisión guarda las fotos estándar enviadas");
        worker.processDue();
        Object[] r = review(event);
        assertEquals("PASSED", r[0]);
        assertEquals("PASS", r[1]);
        assertEquals(true, r[3]);
        assertTrue(r[4].toString().contains("simulado"));
        Object second = em.createNativeQuery("select id from patrol_checkpoint_standard_image where checkpoint_id=:c and position=2")
            .setParameter("c", UUID.fromString(ids.get("checkpointId").toString())).getSingleResult();
        assertEquals(second, em.createNativeQuery("select matched_standard_image_id from visual_review where task_execution_id=:e").setParameter("e", event).getSingleResult(),
            "coincide la foto estándar n.º 2, idéntica a la del agente");
    }

    @Test void checkpointWithoutStandardImagesCannotBeReviewed() throws Exception {
        Map<String,Object> ids = VisintFixtures.publishVisintPatrol();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from patrol_checkpoint_standard_image where checkpoint_id=:c")
            .setParameter("c", UUID.fromString(ids.get("checkpointId").toString())).executeUpdate());
        UUID event = VisintFixtures.executeWithPhoto(em, ids, SAME);
        assertEquals("ERROR_FINAL", review(event)[0]);
    }

    @Test void failVerdictIsStored() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), OTHER);
        mock.forceNextResult("FAIL");
        worker.processDue();
        assertEquals("FAILED", review(event)[0]);
        assertEquals("FAIL", review(event)[1]);
    }

    @Test void visintErrorIsFinal() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), OTHER);
        mock.forceNextResult("ERROR");
        worker.processDue();
        Object[] r = review(event);
        assertEquals("ERROR_FINAL", r[0]);
        assertEquals("ERROR", r[1]);
        makeDue(event);
        worker.processDue();
        assertEquals("ERROR_FINAL", review(event)[0], "un ERROR de VISINT no se reintenta solo");
    }

    @Test void retriesAfterFailureThenPasses() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), SAME);
        mock.failNextCalls(1);
        worker.processDue();
        Object[] r = review(event);
        assertEquals("ERROR_RETRYABLE", r[0]);
        assertEquals(1, ((Number) r[2]).intValue());
        assertNotNull(r[5]);
        makeDue(event);
        worker.processDue();
        assertEquals("PASSED", review(event)[0]);
    }

    @Test void givesUpAfterMaxAttempts() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), SAME);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update visual_review set attempts=4 where task_execution_id=:e").setParameter("e", event).executeUpdate());
        mock.failNextCalls(1);
        worker.processDue();
        Object[] r = review(event);
        assertEquals("ERROR_FINAL", r[0]);
        assertNull(r[1]);
    }

    @Test void executionWithoutVisintHasNoReview() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, OperatorFixtures.publishPatrolWithCheckpoint(-2.17, -79.92), SAME);
        assertEquals(0, ((Number) em.createNativeQuery("select count(*) from visual_review where task_execution_id=:e").setParameter("e", event).getSingleResult()).intValue());
    }
}
