package com.cajamarca.sgi.comando.operator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ReliefContractTest {
 final UUID employee=UUID.randomUUID(),country=UUID.randomUUID();
 final Instant now=Instant.parse("2026-09-24T15:00:00Z");
 ObjectNode batch(boolean unilateral) {
  ObjectMapper m=new ObjectMapper(); ObjectNode b=m.createObjectNode();
  b.put("batchId",UUID.randomUUID().toString()).put("correlationId",UUID.randomUUID().toString()).put("employeeId",employee.toString()).put("instanceCountryId",country.toString()).put("deviceId","test").put("capturedAt",now.toString());
  ObjectNode e=b.putArray("events").addObject();
  for(String k:new String[]{"eventId","assignmentId","postId","shiftOccurrenceId"})e.put(k,UUID.randomUUID().toString());
  e.put("type","RELIEF_SUBMITTED").put("incomingEmployeeId",employee.toString()).put("inventoryStatus","PENDING_SOURCE").put("unilateral",unilateral).put("executedAt",now.toString());
  if(unilateral)e.put("unilateralReason","Saliente ausente");else e.put("outgoingEmployeeId",UUID.randomUUID().toString());
  ArrayNode photos=e.putArray("evidence"); for(String p:ReliefContract.PURPOSES)if(!unilateral||!p.startsWith("outgoing"))photos.addObject().put("purpose",p).put("evidenceId",UUID.randomUUID().toString());
  e.putArray("consignmentReadings");return b;
 }
 ObjectNode event(ObjectNode b){return (ObjectNode)b.path("events").get(0);}
 @Test void acceptsBilateralAndUnilateral(){assertDoesNotThrow(()->ReliefContract.validate(batch(true),employee,country,now));assertDoesNotThrow(()->ReliefContract.validate(batch(false),employee,country,now));}
 @Test void refusesMissingPhotos(){var b=batch(true);event(b).putArray("evidence");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
 @Test void refusesInventedInventoryCompletion(){var b=batch(true);event(b).put("inventoryStatus","OK");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
 @Test void refusesSelfRelief(){var b=batch(false);event(b).put("outgoingEmployeeId",employee.toString());assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
 @Test void refusesCrossTenant(){assertThrows(jakarta.ws.rs.ForbiddenException.class,()->ReliefContract.validate(batch(true),employee,UUID.randomUUID(),now));}
 @Test void refusesMissingUnilateralReason(){var b=batch(true);event(b).remove("unilateralReason");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
}
