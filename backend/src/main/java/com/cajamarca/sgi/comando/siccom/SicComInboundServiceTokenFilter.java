package com.cajamarca.sgi.comando.siccom;

import com.cajamarca.sgi.comando.interconnections.CredentialRefResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.WebApplicationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Technical ingress adapter for the SIC:COM service identity. The effective
 * credential is obtained only through credential_ref at runtime.
 */
@ApplicationScoped
public class SicComInboundServiceTokenFilter {

  @ConfigProperty(name = "sgi.sic-com.inbound.credential-ref")
  Optional<String> credentialRef;

  @Inject CredentialRefResolver credentials;

  public void assertAuthorized(String authorization) {
    if (credentialRef.isEmpty() || credentialRef.get().isBlank()) {
      throw rejected(Response.Status.SERVICE_UNAVAILABLE, "SIC_COM service identity is not configured for this environment.");
    }

    final String expected;
    try {
      expected = credentials.resolve(credentialRef.get());
    } catch (InterconnectionException e) {
      throw rejected(Response.Status.SERVICE_UNAVAILABLE, "SIC_COM service identity is not available for this environment.");
    }

    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw rejected(Response.Status.UNAUTHORIZED, "SIC_COM service token is required.");
    }

    String supplied = authorization.substring("Bearer ".length());
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
      throw rejected(Response.Status.UNAUTHORIZED, "SIC_COM service token is invalid.");
    }
  }

  private static WebApplicationException rejected(Response.Status status, String error) {
    Response.ResponseBuilder response = Response.status(status)
        .entity(Map.of("error", error))
        .type("application/json");
    if (status == Response.Status.UNAUTHORIZED) response.header("WWW-Authenticate", "Bearer");
    return new WebApplicationException(response.build());
  }
}
