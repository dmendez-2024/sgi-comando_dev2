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
 @Test void inventoryIsOptional(){var b=batch(false);event(b).remove("inventoryStatus");assertDoesNotThrow(()->ReliefContract.validate(b,employee,country,now));assertEquals(ReliefContract.INVENTORY_NOT_REPORTED,ReliefContract.inventoryStatus(event(b)));
  var empty=batch(false);event(empty).put("inventoryStatus","");event(empty).putArray("inventoryItems");assertDoesNotThrow(()->ReliefContract.validate(empty,employee,country,now));}
 @Test void refusesInventoryItemsWithoutStatus(){var b=batch(false);event(b).remove("inventoryStatus");event(b).putArray("inventoryItems").addObject().put("assetId",UUID.randomUUID().toString()).put("condition","GOOD");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
 @Test void reportedInventoryStillRequiresItems(){var b=batch(false);event(b).put("inventoryStatus","COMPLETE");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
 @Test void refusesSelfRelief(){var b=batch(false);event(b).put("outgoingEmployeeId",employee.toString());assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
 @Test void refusesCrossTenant(){assertThrows(jakarta.ws.rs.ForbiddenException.class,()->ReliefContract.validate(batch(true),employee,UUID.randomUUID(),now));}
 ObjectNode opening(){var b=batch(true);event(b).remove("unilateralReason");event(b).put("unilateral",false).put("reliefMode",ReliefContract.MODE_OPENING).put("openingCause",ReliefContract.CAUSE_GAP);return b;}
 @Test void acceptsOpeningWithoutOutgoingPhotosNorReason(){assertDoesNotThrow(()->ReliefContract.validate(opening(),employee,country,now));}
 @Test void openingRequiresAFormalCause(){var b=opening();event(b).put("openingCause","Sin saliente");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));
  var missing=opening();event(missing).remove("openingCause");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(missing,employee,country,now));}
 @Test void openingRefusesAnOutgoingAgent(){var b=opening();event(b).put("outgoingEmployeeId",UUID.randomUUID().toString());assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
 @Test void modeMustMatchUnilateralFlag(){var b=opening();event(b).put("unilateral",true).put("unilateralReason","x");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));
  var normal=batch(false);event(normal).put("reliefMode","OTRO");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(normal,employee,country,now));}
 @Test void legacyEventsWithoutModeKeepTheirMeaning(){assertEquals(ReliefContract.MODE_UNILATERAL,ReliefContract.mode(event(batch(true))));assertEquals(ReliefContract.MODE_NORMAL,ReliefContract.mode(event(batch(false))));}
 ObjectNode inventory(Integer version,String condition,String observation,boolean photo){var b=batch(false);event(b).put("inventoryStatus","REPORTED");if(version!=null)event(b).put("inventoryContractVersion",version);
  ObjectNode item=event(b).putArray("inventoryItems").addObject().put("assetId",UUID.randomUUID().toString()).put("condition",condition);
  if(observation!=null)item.put("observation",observation);ArrayNode ids=item.putArray("evidenceIds");if(photo)ids.add(UUID.randomUUID().toString());return b;}
 @Test void acceptsWithIssueCondition(){assertDoesNotThrow(()->ReliefContract.validate(inventory(2,"WITH_ISSUE","Antena floja",true),employee,country,now));}
 @Test void v1KeepsAcceptingItemsWithoutPhotoNorObservation(){assertDoesNotThrow(()->ReliefContract.validate(inventory(null,"MISSING",null,false),employee,country,now));
  assertDoesNotThrow(()->ReliefContract.validate(inventory(1,"GOOD",null,false),employee,country,now));}
 @Test void v2RequiresAPhotoPerAsset(){assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(inventory(2,"GOOD",null,false),employee,country,now));
  assertDoesNotThrow(()->ReliefContract.validate(inventory(2,"GOOD",null,true),employee,country,now));}
 @Test void v2RequiresObservationForIssuesDamageAndMissing(){for(String c:ReliefContract.CONDITIONS_REQUIRING_OBSERVATION){
   assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(inventory(2,c,null,true),employee,country,now));
   assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(inventory(2,c,"  ",true),employee,country,now));
   assertDoesNotThrow(()->ReliefContract.validate(inventory(2,c,"Detalle",true),employee,country,now));}}
 @Test void refusesUnknownInventoryContractVersion(){assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(inventory(3,"GOOD",null,true),employee,country,now));}
 @Test void refusesMissingUnilateralReason(){var b=batch(true);event(b).remove("unilateralReason");assertThrows(jakarta.ws.rs.BadRequestException.class,()->ReliefContract.validate(b,employee,country,now));}
}
