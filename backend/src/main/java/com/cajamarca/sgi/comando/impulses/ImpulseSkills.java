package com.cajamarca.sgi.comando.impulses;

import java.util.List;

/** Las ocho habilidades de Mi Perfil → Impulsos. Cada 100 Impulsos suben 0,1 el nivel de la habilidad, hasta 5,0. */
public final class ImpulseSkills {
    public record Skill(String code, String name) {}

    public static final List<Skill> ALL = List.of(
        new Skill("ASISTENCIA", "Asistencia"),
        new Skill("CONTROL_ACCESO", "Control de Acceso"),
        new Skill("PATRULLAS", "Patrullas Operativas"),
        new Skill("CRITERIO", "Criterio Operativo"),
        new Skill("TACTICA", "Condición Táctica"),
        new Skill("PORTE", "Porte Cajamarca"),
        new Skill("LIDERAZGO", "Liderazgo"),
        new Skill("ATENCION_CLIENTE", "Atención al Cliente"));

    /** Impulsos por cada décima de nivel (1000 Impulsos = 1 punto de habilidad, hoja AdS). */
    public static final int IMPULSES_PER_TENTH = 100;
    public static final int MAX_TENTH = 50;

    public static String name(String code) {
        return ALL.stream().filter(s -> s.code().equals(code)).map(Skill::name).findFirst().orElse(code);
    }

    private ImpulseSkills() {}
}
