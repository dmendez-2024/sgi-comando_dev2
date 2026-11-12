package com.cajamarca.sgi.comando.rrhh;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EmployeePhotoServiceTest {
    @Test
    void acceptsRawJpegAndPngDataUrl() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
        var decodedJpeg = EmployeePhotoService.decode(Base64.getEncoder().encodeToString(jpeg), 1024);
        assertEquals("image/jpeg", decodedJpeg.contentType());
        assertArrayEquals(jpeg, decodedJpeg.bytes());
        assertEquals(64, decodedJpeg.sha256().length());

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1};
        var decodedPng = EmployeePhotoService.decode(
            "data:image/png;base64," + Base64.getEncoder().encodeToString(png), 1024
        );
        assertEquals("image/png", decodedPng.contentType());
        assertArrayEquals(png, decodedPng.bytes());
    }

    @Test
    void omittedOrBlankPhotoPreservesExistingPhoto() {
        assertNull(EmployeePhotoService.decode(null, 1024));
        assertNull(EmployeePhotoService.decode("   ", 1024));
    }

    @Test
    void rejectsInvalidUnsupportedAndOversizedPayloads() {
        assertThrows(BadRequestException.class, () -> EmployeePhotoService.decode("not-base64", 1024));
        assertThrows(BadRequestException.class, () -> EmployeePhotoService.decode(
            Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}), 1024
        ));
        byte[] oversizedJpeg = new byte[11];
        oversizedJpeg[0] = (byte) 0xff;
        oversizedJpeg[1] = (byte) 0xd8;
        oversizedJpeg[2] = (byte) 0xff;
        assertThrows(BadRequestException.class, () -> EmployeePhotoService.decode(
            Base64.getEncoder().encodeToString(oversizedJpeg), 10
        ));
    }
}
