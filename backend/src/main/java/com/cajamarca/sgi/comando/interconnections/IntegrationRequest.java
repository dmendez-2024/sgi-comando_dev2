package com.cajamarca.sgi.comando.interconnections;

import java.util.HashMap;
import java.util.Map;

public class IntegrationRequest {
    public Object body;
    public String idempotencyKey;
    public String correlationId;
    public Map<String,String> pathParams = new HashMap<>();
    public Map<String,String> queryParams = new HashMap<>();
    public Map<String,String> headers = new HashMap<>();
}
