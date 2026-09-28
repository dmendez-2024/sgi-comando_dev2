package com.cajamarca.sgi.comando.assignments;

import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class AssignmentRolePolicy {
    private static final Set<String> ASSIGNABLE_KEYS = Set.of(
        key("Agente de Seguridad"),
        key("Agente de Consola de Monitoreo Senior"),
        key("Supervisor de Seguridad"),
        key("Supervisor de Seguridad CL"),
        key("Escolta Líder"),
        key("Escolta Líder JT"),
        key("Responsable de Punto")
    );

    private static final List<String> ASSIGNABLE_ROLE_CODES = List.of(
        "Agente de Seguridad",
        "Agente de Consola de Monitoreo Senior",
        "Supervisor de Seguridad",
        "Supervisor de Seguridad CL",
        "Escolta Líder",
        "Escolta Líder JT",
        "Responsable de Punto",
        "AGENTE_SEGURIDAD",
        "SUPERVISOR_SEGURIDAD",
        "ESCOLTA_SEGURIDAD"
    );

    public List<String> assignableRoleCodes() { return ASSIGNABLE_ROLE_CODES; }

    public boolean isAssignable(String roleCode) { return ASSIGNABLE_KEYS.contains(key(roleCode)); }

    public boolean matchesRequiredRole(String requiredRoleCode, String employeeRoleCode) {
        String required = key(requiredRoleCode);
        String employee = key(employeeRoleCode);
        if (required.equals(employee)) return true;
        return switch (required) {
            case "AGENTE_SEGURIDAD" -> employee.equals(key("Agente de Seguridad"))
                || employee.equals(key("Agente de Consola de Monitoreo Senior"));
            case "SUPERVISOR_SEGURIDAD" -> employee.equals(key("Supervisor de Seguridad"))
                || employee.equals(key("Supervisor de Seguridad CL"));
            case "ESCOLTA_SEGURIDAD" -> employee.equals(key("Escolta Líder"))
                || employee.equals(key("Escolta Líder JT"));
            default -> false;
        };
    }

    private static String key(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
    }
}
