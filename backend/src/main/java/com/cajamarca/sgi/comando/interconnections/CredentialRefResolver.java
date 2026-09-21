package com.cajamarca.sgi.comando.interconnections;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Resolves a credential_ref without placing secrets in SITC or business code.
 * Production may replace this implementation with Vault/KMS while keeping the same contract.
 */
@ApplicationScoped
public class CredentialRefResolver {
    public String resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) return null;
        String key = "SGI_CREDENTIAL_" + credentialRef.toUpperCase().replaceAll("[^A-Z0-9]", "_");
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new InterconnectionException("CREDENTIAL_NOT_AVAILABLE", "Credential reference is configured but no runtime secret is available");
        }
        return value;
    }
}
