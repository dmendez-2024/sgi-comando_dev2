package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.interconnections.GenericInterconnectionExecutor;
import com.cajamarca.sgi.comando.interconnections.IntegrationRequest;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhoEmployeePhotoAdapterTest {

    @Test
    void sendsOnlyPersonaIdAndReturnsValidatedImage() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};
        CapturingExecutor executor = new CapturingExecutor(result(200, response(12345L, "image/jpeg", "jpg", jpeg)));

        DhoEmployeePhotoAdapter.EmployeePhoto photo = adapter(executor).fetch(12345L);

        assertArrayEquals(jpeg, photo.bytes());
        assertEquals("image/jpeg", photo.contentType());
        assertEquals("SGI_COM_SIC_DHO_0002_v001", executor.interconnectionId);
        assertEquals("SGI_COM_SIC_DHO_0002_IF01", executor.interfaceId);
        assertEquals(Map.of("personaId", 12345L), executor.request.body);
        assertEquals("employee-photo:12345", executor.request.idempotencyKey);
        assertEquals(1, executor.request.maxRetriesOverride);
        assertTrue(executor.request.retryOnlyTimeouts);
        assertFalse(executor.request.retryServerErrors);
    }

    @Test
    void returnsNullWhenDhoReportsNoPhoto() {
        CapturingExecutor executor = new CapturingExecutor(result(200,
            "{\"success\":false,\"message\":\"Sin foto\",\"personaId\":12345}"));

        assertNull(adapter(executor).fetch(12345L));
    }

    @Test
    void rejectsPhotoForAnotherPersona() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        CapturingExecutor executor = new CapturingExecutor(result(200, response(999L, "image/jpeg", "jpg", jpeg)));

        InterconnectionException error = assertThrows(InterconnectionException.class,
            () -> adapter(executor).fetch(12345L));

        assertEquals("DHO_EMPLOYEE_PHOTO_INVALID_RESPONSE", error.code());
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchImageBytes() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        CapturingExecutor executor = new CapturingExecutor(result(200, response(12345L, "image/jpeg", "jpg", png)));

        InterconnectionException error = assertThrows(InterconnectionException.class,
            () -> adapter(executor).fetch(12345L));

        assertEquals("DHO_EMPLOYEE_PHOTO_INVALID_RESPONSE", error.code());
    }

    @Test
    void exposesRemoteHttpFailureAsInterconnectionError() {
        CapturingExecutor executor = new CapturingExecutor(result(503, "{}"));

        InterconnectionException error = assertThrows(InterconnectionException.class,
            () -> adapter(executor).fetch(12345L));

        assertEquals("DHO_EMPLOYEE_PHOTO_HTTP_503", error.code());
    }

    private static DhoEmployeePhotoAdapter adapter(CapturingExecutor executor) {
        DhoEmployeePhotoAdapter adapter = new DhoEmployeePhotoAdapter();
        adapter.executor = executor;
        adapter.mapper = new ObjectMapper();
        return adapter;
    }

    private static String response(long personaId, String contentType, String extension, byte[] bytes) throws Exception {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("success", true);
        response.put("message", "Foto de perfil consultada correctamente.");
        response.put("personaId", personaId);
        response.put("contentType", contentType);
        response.put("fileExtension", extension);
        response.put("photoBase64", Base64.getEncoder().encodeToString(bytes));
        return new ObjectMapper().writeValueAsString(response);
    }

    private static IntegrationResult result(int status, String body) {
        IntegrationResult result = new IntegrationResult();
        result.statusCode = status;
        result.body = body;
        return result;
    }

    static class CapturingExecutor extends GenericInterconnectionExecutor {
        private final IntegrationResult result;
        String interconnectionId;
        String interfaceId;
        IntegrationRequest request;

        CapturingExecutor(IntegrationResult result) { this.result = result; }

        @Override
        public IntegrationResult execute(String interconnectionId, String interfaceId, IntegrationRequest request) {
            this.interconnectionId = interconnectionId;
            this.interfaceId = interfaceId;
            this.request = request;
            return result;
        }
    }
}
