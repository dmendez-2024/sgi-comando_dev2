package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.storage.Digests;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.*;

/**
 * VISINT simulado (solo UAT) mientras no exista el servicio real. Determinista: usa las huellas sha256,
 * no analiza imágenes. PASS si la foto del agente es idéntica a alguna foto estándar (y la señala); si no, PASS/FAIL/ERROR
 * según la huella combinada.
 */
@ApplicationScoped
public class MockVisintAdapter implements VisintPort {
    @ConfigProperty(name="sgi.visint.mock.latency-ms", defaultValue="0") long latencyMs;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicReference<String> forced = new AtomicReference<>();

    /** Las próximas n llamadas no responden (para pruebas y demos de reintento). */
    public void failNextCalls(int n) { failures.set(n); }
    /** La próxima llamada responde este resultado (PASS, FAIL o ERROR). */
    public void forceNextResult(String result) { forced.set(result); }

    @Override
    public ReviewResult review(ReviewRequest request) {
        if (failures.getAndUpdate(x -> Math.max(0, x - 1)) > 0) throw new VisintUnavailableException("VISINT simulado: sin respuesta (falla forzada)");
        if (latencyMs > 0) { try { Thread.sleep(latencyMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        String result = forced.getAndSet(null);
        String photo = request.evidence().sha256();
        UUID matched = request.standards().stream().filter(s -> s.sha256().equals(photo)).map(StandardRef::imageId).findFirst().orElse(null);
        if (result == null) {
            if (matched != null) result = "PASS";
            else {
                StringBuilder all = new StringBuilder(photo);
                request.standards().forEach(s -> all.append(s.sha256()));
                char c = Digests.sha256Hex(all.toString().getBytes(StandardCharsets.UTF_8)).charAt(0);
                result = c <= '9' ? "PASS" : c <= 'd' ? "FAIL" : "ERROR";
            }
        }
        String findings = switch (result) {
            case "PASS" -> "Cumple con el estándar (VISINT simulado)";
            case "FAIL" -> "No cumple con el estándar (VISINT simulado)";
            default -> "No se pudo evaluar la imagen (VISINT simulado)";
        };
        if (matched == null && result.equals("PASS") && !request.standards().isEmpty()) matched = request.standards().get(0).imageId();
        String reason = switch (result) { case "PASS" -> "OK"; case "FAIL" -> "FAIL_NO_MATCH"; default -> "ERROR_VISINT"; };
        return new ReviewResult("mock-" + request.reviewId(), result, findings, result.equals("PASS") ? matched : null, reason, "visint-simulado");
    }
}
