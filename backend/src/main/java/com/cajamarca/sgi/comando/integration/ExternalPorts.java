package com.cajamarca.sgi.comando.integration;

import java.util.*;

/**
 * Puertos de integración. El dominio depende de estas interfaces, no de HTTP/colas concretas.
 * Las implementaciones UAT pueden ser LOCAL/MOCK; producción sustituye adapters sin reescribir dominio.
 */
public final class ExternalPorts {
    private ExternalPorts() {}

    public interface CorePort {
        Map<String,Object> instanceCountry(UUID instanceCountryId);
    }
    public interface SicComPort {
        List<Map<String,Object>> services(UUID instanceCountryId);
        List<Map<String,Object>> points(UUID instanceCountryId, UUID serviceId);
        List<Map<String,Object>> posts(UUID instanceCountryId, UUID pointId);
    }
    public interface SicRrhhPort {
        Optional<Map<String,Object>> employee(UUID instanceCountryId, UUID employeeId);
    }
    public interface SicRrmmPort {
        List<Map<String,Object>> expectedAssets(UUID instanceCountryId, UUID postId);
        Map<String,Object> reportObservedState(UUID instanceCountryId, UUID clientRequestId, Map<String,Object> report);
    }
    public interface AtsPort {
        Optional<Map<String,Object>> effectiveSnapshot(UUID instanceCountryId, UUID pointId);
    }
    public interface StcPort {
        String createWork(UUID instanceCountryId, UUID workOriginId, Map<String,Object> payload);
    }
    public interface SmcPort {
        void publishFact(UUID instanceCountryId, String factType, Map<String,Object> payload);
    }
}
