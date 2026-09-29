package com.cajamarca.sgi.comando.storage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@QuarkusTest
class StorageServiceTest {
  @Inject StorageService storage;
  @Test void putReadDelete() {
    String key = "test/" + UUID.randomUUID() + ".bin";
    storage.put(storage.evidenceBucket(), key, new byte[]{1,2,3}, "application/octet-stream");
    assertTrue(storage.exists(storage.evidenceBucket(), key));
    assertArrayEquals(new byte[]{1,2,3}, storage.read(storage.evidenceBucket(), key));
    storage.delete(storage.evidenceBucket(), key);
    assertFalse(storage.exists(storage.evidenceBucket(), key));
    assertThrows(NotFoundException.class, () -> storage.read(storage.evidenceBucket(), key));
  }
}
