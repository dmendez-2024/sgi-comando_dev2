package com.cajamarca.sgi.comando.companies;

import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyResponsibleRolesTest {

    @Test
    void separatesCoordinatorAndAssistantRoles() {
        assertTrue(CompanyResource.isCoordinatorRole("COORDINADOR_COMPANIA"));
        assertTrue(CompanyResource.isCoordinatorRole("Coordinador de Compañía"));
        assertFalse(CompanyResource.isCoordinatorRole("Asistente de Operaciones"));

        assertTrue(CompanyResource.isAssistantRole("ASISTENTE_COORDINACION"));
        assertTrue(CompanyResource.isAssistantRole("Asistente de Operaciones"));
        assertFalse(CompanyResource.isAssistantRole("Coordinador de Compañía"));
    }

    @Test
    void coordinatorIsRequiredOnlyWhenAnEligibleOptionExists() {
        EmployeeOperationalSnapshot assistant = employee("Asistente de Operaciones");
        EmployeeOperationalSnapshot coordinator = employee("Coordinador de Compañía");

        assertFalse(CompanyResource.coordinatorRequired(List.of()));
        assertFalse(CompanyResource.coordinatorRequired(List.of(assistant)));
        assertTrue(CompanyResource.coordinatorRequired(List.of(assistant, coordinator)));
    }

    private static EmployeeOperationalSnapshot employee(String roleCode) {
        EmployeeOperationalSnapshot employee = new EmployeeOperationalSnapshot();
        employee.roleCode = roleCode;
        return employee;
    }
}
