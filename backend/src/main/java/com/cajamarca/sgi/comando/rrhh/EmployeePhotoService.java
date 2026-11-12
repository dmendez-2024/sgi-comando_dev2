package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.storage.Digests;
import com.cajamarca.sgi.comando.storage.ImageSniffer;
import com.cajamarca.sgi.comando.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Base64;
import java.util.UUID;

/** Valida y almacena la foto de perfil que DHO incluye de forma condicional. */
@ApplicationScoped
public class EmployeePhotoService {
    @Inject StorageService storage;

    @ConfigProperty(name = "sgi.integrations.rrhh.photo-max-bytes", defaultValue = "5242880")
    long maxBytes;

    record DecodedPhoto(byte[] bytes, String contentType, String sha256) {}
    record StoredPhoto(String bucket, String objectKey, String contentType, String sha256, long sizeBytes) {}

    DecodedPhoto decode(String photoBase64) {
        return decode(photoBase64, maxBytes);
    }

    static DecodedPhoto decode(String photoBase64, long maxBytes) {
        if (photoBase64 == null || photoBase64.isBlank()) return null;
        if (maxBytes <= 0) throw new IllegalStateException("El límite de fotografías de RRHH no es válido.");

        String encoded = photoBase64.trim();
        if (encoded.regionMatches(true, 0, "data:", 0, 5)) {
            int comma = encoded.indexOf(',');
            if (comma < 0) throw new BadRequestException("photoBase64 no contiene un Data URL válido.");
            encoded = encoded.substring(comma + 1).trim();
        }
        if (encoded.isEmpty()) return null;

        long maxEncodedLength = ((maxBytes + 2L) / 3L) * 4L;
        if (encoded.length() > maxEncodedLength) {
            throw new BadRequestException("La fotografía del empleado no puede superar 5 MB.");
        }

        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("photoBase64 no contiene Base64 válido.");
        }
        if (bytes.length == 0) throw new BadRequestException("La fotografía del empleado está vacía.");
        if (bytes.length > maxBytes) {
            throw new BadRequestException("La fotografía del empleado no puede superar 5 MB.");
        }

        String contentType = ImageSniffer.detect(bytes);
        if (contentType == null) {
            throw new BadRequestException("La fotografía del empleado debe ser JPEG, PNG o WebP.");
        }
        return new DecodedPhoto(bytes, contentType, Digests.sha256Hex(bytes));
    }

    StoredPhoto store(UUID instanceCountryId, UUID employeeId, DecodedPhoto photo) {
        String bucket = storage.employeeBucket();
        String objectKey = "employees/" + instanceCountryId + "/" + employeeId + "/"
            + photo.sha256() + "." + ImageSniffer.extension(photo.contentType());
        storage.put(bucket, objectKey, photo.bytes(), photo.contentType());
        return new StoredPhoto(bucket, objectKey, photo.contentType(), photo.sha256(), photo.bytes().length);
    }
}
