package com.cajamarca.sgi.comando.ident;

import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SEG-M2M: destino tomado del codigo de la interconexion (SITC-NOM-001 §9) y fail-closed sin credentialRef vault:. */
class IdentServiceTokensTest {

    @Test void targetFromVersionedCode() {
        assertEquals("SIC_RRMM", IdentServiceTokens.targetOf("SGI_COM_SIC_RRMM_0001_v001"));
        assertEquals("SIC_RRMM", IdentServiceTokens.targetOf("SGI_COM_SIC_RRMM_0002"));
        assertEquals("SIC_RRHH", IdentServiceTokens.targetOf("SGI_COM_SIC_RRHH_0001_v001"));
        assertEquals("", IdentServiceTokens.targetOf("SIC_RRMM_SGI_COM_0001_v001"));
    }

    @Test void withoutClientOrVaultRefNoTokenIsRequested() {
        IdentServiceTokens t = new IdentServiceTokens();
        InterconnectionException e = assertThrows(InterconnectionException.class, () -> t.tokenFor("SISTEMA_SIN_CLIENTE", "vault:kv/x"));
        assertEquals("M2M_CLIENT_NOT_CONFIGURED", e.code());
    }
}
