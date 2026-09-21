package com.cajamarca.sgi.comando.interconnections;

/**
 * Canonical SITC-NOM-001 v3.0 interconnection identifiers for SGI: Comando.
 *
 * The stable reference (without __V0001) is the identity in CORE. The full ID
 * identifies the current master revision. Business code must use these constants
 * instead of hard-coded hosts/endpoints.
 */
public final class InterconnectionIds {
    private InterconnectionIds() {}

    public static final String CORE_CONTEXT = "SGI_COM__CORE__00001__V0001";
    public static final String CORE_RESOLVER = "SGI_COM__CORE__00002__V0001";
    public static final String IDENT_AUTH = "SGI_COM__IDENT__00001__V0001";

    public static final String SIC_COM_RECONCILIATION = "SGI_COM__SIC_COM__00001__V0001";
    public static final String SIC_COM_EVENTS = "SIC_COM__SGI_COM__00001__V0001";

    public static final String RRHH_EMPLOYEE_CONTEXT = "SGI_COM__SIC_RRHH__00001__V0001";
    public static final String RRHH_OPERATIONAL_LABOR_EVENTS = "SGI_COM__SIC_RRHH__00002__V0001";
    public static final String RRHH_MASTER_EVENTS = "SIC_RRHH__SGI_COM__00001__V0001";

    public static final String RRMM_EXPECTED_ASSETS = "SGI_COM__SIC_RRMM__00001__V0001";
    public static final String RRMM_POINT_POST_CATALOG = "SIC_RRMM__SGI_COM__00001__V0001";

    public static final String ATS_PUBLISHED_PACKAGE = "ATS__SGI_COM__00001__V0001";
    public static final String ATS_OPERATIONAL_RECONCILIATION = "SGI_COM__ATS__00001__V0001";

    public static final String SMC_OPERATIONAL_FACTS = "SGI_COM__SMC__00001__V0001";
    public static final String SMC_KPI_QUERY = "SGI_COM__SMC__00002__V0001";

    public static final String STC_CREATE_CASE = "SGI_COM__STC__00001__V0001";
    public static final String STC_CASE_STATUS = "SGI_COM__STC__00002__V0001";

    public static final String VISINT_REVIEW_REQUEST = "SGI_COM__VISINT__00001__V0001";
    public static final String VISINT_REVIEW_RESULT = "SGI_COM__VISINT__00002__V0001";

    public static final String OPR_RUNTIME_SYNC = "SGI_OPR__SGI_COM__00001__V0001";
    public static final String OPR_EXECUTION_SUBMISSION = "SGI_OPR__SGI_COM__00002__V0001";

    public static final String CLIENT_OPERATIONAL_READ = "SGI_CLT__SGI_COM__00001__V0001";
    public static final String CLIENT_PROPOSAL_SUBMISSION = "SGI_CLT__SGI_COM__00002__V0001";

    public static final String CM_CON_SHIFT_CONFIRMATION_REQUEST = "SGI_COM__CM_CON__00001__V0001";
    public static final String CM_CON_SHIFT_CONFIRMATION_STATUS = "SGI_COM__CM_CON__00002__V0001";
}
