package com.cajamarca.sgi.comando.obsint;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.cajamarca.sgi.comando.obsint.ObsintMessageReceipts.Via.*;
class ObsintMessageStateTest {
    @Test void agregadoExigeElMenorAvanceDeLosAlcanzables() {
        assertEquals("FAILED",ObsintAgentMessageRecipient.aggregate(List.of("FAILED","FAILED")));
        assertEquals("DELIVERED",ObsintAgentMessageRecipient.aggregate(List.of("READ","DELIVERED","FAILED")));
        assertEquals("PENDING",ObsintAgentMessageRecipient.aggregate(List.of("READ","PENDING")));
        assertEquals("SENT",ObsintAgentMessageRecipient.aggregate(List.of("READ","SENT")));
        assertEquals("READ",ObsintAgentMessageRecipient.aggregate(List.of("READ","FAILED")));
    }
    @Test void recibosIdempotentesPriorizanLaIdentidadDeLaApp() {
        var r=new ObsintAgentMessageRecipient();r.status="PENDING";Instant now=Instant.now();
        ObsintMessageReceipts.applyState(r,"READ",SOCKET_ACK,now,now);
        ObsintMessageReceipts.applyState(r,"DELIVERED",OPERATOR_API,now.plusSeconds(10),now.plusSeconds(10));
        ObsintMessageReceipts.applyState(r,"READ",OPERATOR_API,now.plusSeconds(20),now.plusSeconds(20));
        assertEquals("READ",r.status);assertEquals(now,r.readAt);assertEquals(now,r.deliveredAt);assertEquals("OPERATOR_API",r.readVia);
        ObsintMessageReceipts.applyState(r,"READ",SOCKET_ACK,now,now);assertEquals("OPERATOR_API",r.readVia);
    }
    @Test void lecturaRecuperaVencidosPeroNoAgentesSinVinculo() {
        var r=new ObsintAgentMessageRecipient();r.status="FAILED";r.lastError="EXPIRED";Instant now=Instant.now();
        ObsintMessageReceipts.applyState(r,"READ",OPERATOR_API,now.plusSeconds(500),now);
        assertEquals("READ",r.status);assertEquals(now,r.readAt);assertNull(r.lastError);
        var missing=new ObsintAgentMessageRecipient();missing.status="FAILED";missing.lastError="NO_PERSONA_LINK";
        ObsintMessageReceipts.applyState(missing,"READ",SOCKET_ACK,now,now);assertEquals("FAILED",missing.status);assertNull(missing.readAt);
        ObsintMessageReceipts.applyState(r,"REJECTED",SOCKET_ACK,now,now);assertEquals("READ",r.status);assertEquals("APP_REJECTED",r.lastError);assertEquals(now,r.appRejectedAt);
    }
    @Test void reintentosEstanAcotados() {
        assertEquals(Duration.ofSeconds(5),ObsintMessageDispatcher.backoff(1));assertEquals(Duration.ofMinutes(5),ObsintMessageDispatcher.backoff(30));
    }
}
