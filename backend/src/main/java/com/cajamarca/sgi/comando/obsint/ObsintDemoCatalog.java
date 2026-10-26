package com.cajamarca.sgi.comando.obsint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo DEMO (solo con herramientas UAT): equivale al mockup OBSINT v4, tres consignas de 60 s sobre cuatro cámaras cada una.
 * Se crea por tenant la primera vez que se usa la consola, porque el instanceCountryId llega de CORE y no se conoce al migrar.
 * Desaparece cuando exista la configuración Seguridad Electrónica / OBSINT del Punto.
 */
@ApplicationScoped
public class ObsintDemoCatalog {
    @Inject ObsintPlatform platform;

    static final List<String> INSTRUCTIONS = List.of(
        "El usuario no puede ingresar con ninguna maleta, mochila, bolso, chompa, abrigo.",
        "Revisar cualquier movimiento sospechoso.",
        "No debe existir vehículo estacionado en ninguna de estas escenas.");

    @Transactional
    public void ensure(UUID instanceCountryId) {
        if (!platform.demoEnabled() || ObsintConsigna.count("instanceCountryId", instanceCountryId) > 0) return;
        for (int g = 1; g <= INSTRUCTIONS.size(); g++) {
            ObsintConsigna consigna = new ObsintConsigna();
            consigna.instanceCountryId = instanceCountryId;
            consigna.code = "OBS-%03d".formatted(g);
            consigna.title = "Consigna demo " + g;
            consigna.instruction = INSTRUCTIONS.get(g - 1);
            consigna.timeLimitSeconds = 60;
            consigna.position = g;
            for (int c = 1; c <= 4; c++) {
                ObsintCamera camera = new ObsintCamera();
                camera.instanceCountryId = instanceCountryId;
                camera.code = "CAM-%d-%d".formatted(g, c);
                camera.name = "Cámara %02d".formatted(c);
                camera.location = "Grupo %d-%d".formatted(g, c);
                // Relativa a la base de la web (frontend/public/obsint/videos): mismo origen, así se puede capturar evidencia.
                camera.streamUrl = "obsint/videos/%d-%d.mp4".formatted(g, c);
                camera.persist();
                consigna.cameras.add(camera);
            }
            consigna.persist();
        }
    }
}
