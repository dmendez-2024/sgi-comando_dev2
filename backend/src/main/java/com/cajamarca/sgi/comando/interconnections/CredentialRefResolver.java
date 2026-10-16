package com.cajamarca.sgi.comando.interconnections;

import com.cajamarca.sgi.comando.ident.OpenBaoSecretReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves a credential_ref without placing secrets in SITC or business code.
 * SEG-M2M: una referencia vault:kv/data/... se lee de OpenBao (AppRole propio de SGI_COM); el resto sigue como antes
 * (variable SGI_CREDENTIAL_<REF>) para no romper las referencias existentes.
 */
@ApplicationScoped
public class CredentialRefResolver {
    @Inject OpenBaoSecretReader openBao;

    public String resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) return null;
        if (credentialRef.trim().startsWith("vault:") && openBao != null) {
            try {
                return openBao.read(credentialRef.trim());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterconnectionException("CREDENTIAL_NOT_AVAILABLE", "Interrupted while reading the credential from OpenBao");
            } catch (Exception e) {
                throw new InterconnectionException("CREDENTIAL_NOT_AVAILABLE", "Credential reference could not be read from OpenBao");
            }
        }
        String key = "SGI_CREDENTIAL_" + credentialRef.toUpperCase().replaceAll("[^A-Z0-9]", "_");
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new InterconnectionException("CREDENTIAL_NOT_AVAILABLE", "Credential reference is configured but no runtime secret is available");
        }
        return value;
    }
}
