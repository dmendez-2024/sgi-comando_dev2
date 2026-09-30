package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.*;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Contrato del FormData de evidencias: campo "metadata" (JSON como texto) + partes "files".
 * Cada archivo se llama "&lt;clientEvidenceId&gt;.&lt;ext&gt;" y se empareja con su ítem por nombre, no por posición.
 */
public final class EvidenceUploadContract {
    private EvidenceUploadContract() {}
    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{64}$");

    public record Item(UUID clientEvidenceId, Instant capturedAt, Double latitude, Double longitude, Double accuracyM, String source, String sha256) {}
    public record Metadata(UUID uploadBatchId, UUID eventId, UUID assignmentId, String targetType, UUID targetId, List<Item> items) {}

    public static Metadata parse(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) throw new BadRequestException("Metadatos inválidos: el campo metadata es obligatorio");
        try {
            JsonNode n = mapper.readTree(json);
            List<Item> items = new ArrayList<>();
            for (JsonNode i : n.path("items")) items.add(new Item(ReliefContract.uuid(i, "clientEvidenceId"), ReliefContract.time(i, "capturedAt"),
                num(i, "latitude"), num(i, "longitude"), num(i, "accuracyM"), ReliefContract.text(i, "source"), ReliefContract.text(i, "sha256")));
            return new Metadata(ReliefContract.uuid(n, "uploadBatchId"), ReliefContract.uuid(n, "eventId"), ReliefContract.uuid(n, "assignmentId"),
                ReliefContract.text(n, "targetType"), ReliefContract.uuid(n, "targetId"), items);
        } catch (BadRequestException e) { throw new BadRequestException("Metadatos inválidos: " + e.getMessage()); }
        catch (Exception e) { throw new BadRequestException("Metadatos inválidos: JSON mal formado"); }
    }

    /** Devuelve clientEvidenceId → índice del archivo en la lista recibida. */
    public static Map<UUID,Integer> matchFiles(Metadata m, List<String> fileNames, int maxFiles, Instant now) {
        if (!Set.of("PATROL_CHECKPOINT", "CONSIGNMENT_EVIDENCE", "LOGBOOK_FIELD").contains(m.targetType())) throw new BadRequestException("Tipo de destino no soportado: " + m.targetType());
        if (m.items().isEmpty() || m.items().size() > maxFiles) throw new BadRequestException("Se admiten entre 1 y " + maxFiles + " fotos por envío");
        if (fileNames.size() != m.items().size()) throw new BadRequestException("La cantidad de archivos no coincide con los metadatos");
        Map<String,Integer> byName = new HashMap<>();
        for (int i = 0; i < fileNames.size(); i++) {
            String f = fileNames.get(i) == null ? "" : fileNames.get(i);
            int dot = f.lastIndexOf('.');
            if (byName.put((dot > 0 ? f.substring(0, dot) : f).toLowerCase(Locale.ROOT), i) != null) throw new BadRequestException("Archivos duplicados");
        }
        Map<UUID,Integer> result = new LinkedHashMap<>();
        for (Item it : m.items()) {
            if (it.capturedAt().isAfter(now.plusSeconds(300))) throw new BadRequestException("Fecha de captura futura");
            if (!Set.of("CAMERA", "GALLERY").contains(it.source())) throw new BadRequestException("Origen de foto inválido");
            if (!SHA.matcher(it.sha256()).matches()) throw new BadRequestException("sha256 inválido");
            Integer idx = byName.get(it.clientEvidenceId().toString());
            if (idx == null) throw new BadRequestException("Falta el archivo de la foto " + it.clientEvidenceId());
            if (result.put(it.clientEvidenceId(), idx) != null) throw new BadRequestException("clientEvidenceId duplicado");
        }
        return result;
    }

    private static Double num(JsonNode n, String k) { return n.hasNonNull(k) && n.path(k).isNumber() ? n.path(k).asDouble() : null; }
}
