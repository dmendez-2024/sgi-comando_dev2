package com.cajamarca.sgi.comando.interconnections;

import java.util.HashMap;
import java.util.Map;

public class IntegrationRequest {
    public Object body;
    public String idempotencyKey;
    public String correlationId;
    /** Número de reintentos adicionales para esta llamada; null conserva el valor resuelto desde CORE. */
    public Integer maxRetriesOverride;
    /** Permite reintentar respuestas 5xx. */
    public boolean retryServerErrors = true;
    /** Permite reintentar errores de transporte. */
    public boolean retryTransportErrors = true;
    /** Cuando es true, dentro de los errores de transporte solo reintenta timeouts. */
    public boolean retryOnlyTimeouts;
    /** Política por llamada: la credencial debe proceder de OpenBao, sin fallback a variables. */
    public boolean requireOpenBaoCredentials;
    public Map<String,String> pathParams = new HashMap<>();
    public Map<String,String> queryParams = new HashMap<>();
    public Map<String,String> headers = new HashMap<>();
}
