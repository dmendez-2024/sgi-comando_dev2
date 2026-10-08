package com.cajamarca.sgi.comando.interconnections;

import java.util.Objects;

/**
 * Canonical ECOSYSTEM_INTERCONNECTION identifiers for SGI: Comando.
 *
 * SITC-NOM-001 v4.1:
 *   stable reference: ORIGEN_DESTINO_NNNN
 *   versioned ID:     ORIGEN_DESTINO_NNNN_vNNN
 *   interface ID:     ORIGEN_DESTINO_NNNN_IFNN
 *
 * Historical v3 IDs remain accepted only as transition aliases where an inbound
 * peer may still be sending them. New code and CORE bindings must use v4.1 IDs.
 */
public final class InterconnectionIds {
    private InterconnectionIds() {}

    // CORE. 0001 is frozen ecosystem-wide for Impulse rules (see SGI_OPR 0.14.0).
    public static final String CORE_IMPULSE_RULES = "SGI_COM_CORE_0001_v001";
    public static final String CORE_CONTEXT = "SGI_COM_CORE_0002_v001";
    public static final String CORE_RESOLVER = "SGI_COM_CORE_0003_v001";

    public static final String IDENT_AUTH = "SGI_COM_IDENT_0001_v001";
    public static final String IDENT_EMPLOYEE_QUERY = "IDENT_SGI_COM_0001_v001";

    public static final String SIC_COM_RECONCILIATION = "SGI_COM_SIC_COM_0001_v001";
    public static final String SIC_COM_EVENTS = "SIC_COM_SGI_COM_0001_v001";

    public static final String RRHH_EMPLOYEE_CONTEXT = "SGI_COM_SIC_RRHH_0001_v001";
    public static final String RRHH_OPERATIONAL_LABOR_EVENTS = "SGI_COM_SIC_RRHH_0002_v001";
    public static final String DHO_MASTER_EVENTS = "SIC_DHO_SGI_COM_0001_v001";
    public static final String DHO_UNAVAILABILITY_EVENTS = "SIC_DHO_SGI_COM_0002_v001";
    public static final String DHO_PAYROLL_SHIFT_QUERY = "SIC_DHO_SGI_COM_0003_v001";
    public static final String TRANSITIONAL_RRHH_MASTER_EVENTS = "SIC_RRHH_SGI_COM_0001_v001";
	public static final String RRHH_MASTER_EVENTS = "SIC_RRHH_SGI_COM_0001_v001";

    public static final String RRMM_EXPECTED_ASSETS = "SGI_COM_SIC_RRMM_0001_v001";
    public static final String RRMM_STATE_REPORTS = "SGI_COM_SIC_RRMM_0002_v001";
    public static final String RRMM_POINT_POST_CATALOG = "SIC_RRMM_SGI_COM_0001_v001";

    public static final String ATS_PUBLISHED_PACKAGE = "ATS_SGI_COM_0001_v001";
    public static final String ATS_OPERATIONAL_RECONCILIATION = "SGI_COM_ATS_0001_v001";

    public static final String SMC_OPERATIONAL_FACTS = "SGI_COM_SMC_0001_v001";
    public static final String SMC_KPI_QUERY = "SGI_COM_SMC_0002_v001";

    public static final String STC_CREATE_CASE = "SGI_COM_STC_0001_v001";
    public static final String STC_CASE_STATUS = "SGI_COM_STC_0002_v001";

    public static final String VISINT_REVIEW_REQUEST = "SGI_COM_VISINT_0001_v002";
    public static final String VISINT_REVIEW_RESULT = "SGI_COM_VISINT_0002_v001";

    public static final String OPR_RUNTIME_SYNC = "SGI_OPR_SGI_COM_0001_v001";
    public static final String OPR_EXECUTION_SUBMISSION = "SGI_OPR_SGI_COM_0002_v001";

    public static final String CLIENT_OPERATIONAL_READ = "SGI_CLT_SGI_COM_0001_v001";
    public static final String CLIENT_PROPOSAL_SUBMISSION = "SGI_CLT_SGI_COM_0002_v001";

    public static final String CM_CON_SHIFT_CONFIRMATION_REQUEST = "SGI_COM_CM_CON_0001_v001";
    public static final String CM_CON_SHIFT_CONFIRMATION_STATUS = "SGI_COM_CM_CON_0002_v001";

    // v3 aliases: compatibility only; never use for new CORE definitions/bindings.
    public static final String LEGACY_SIC_COM_EVENTS = "SIC_COM__SGI_COM__00001__V0001";
    public static final String LEGACY_RRHH_MASTER_EVENTS = "SIC_RRHH__SGI_COM__00001__V0001";

    public static boolean matches(String supplied, String canonical, String... legacyAliases) {
        if (Objects.equals(supplied, canonical)) return true;
        if (legacyAliases == null) return false;
        for (String alias : legacyAliases) {
            if (Objects.equals(supplied, alias)) return true;
        }
        return false;
    }
}
