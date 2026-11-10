package com.cajamarca.sgi.comando.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Push FCM HTTP v1 a SGI Operador: mensaje de solo datos (sin bloque `notification`, para que la app arme el aviso y abra
 * la sala) al topic del agente. Es de mejor esfuerzo: un fallo no cambia el estado del mensaje, que sigue llegando por el
 * socket y por la sincronización (0033). Nunca se registra el contenido en logs.
 */
@ApplicationScoped
public class PushPublisher {
    private static final Logger LOG=Logger.getLogger(PushPublisher.class);
    @Inject PushConfig config;
    @Inject PushCredentials credentials;
    @Inject ObjectMapper mapper;
    @Inject MeterRegistry metrics;
    private HttpClient http;
    private long lastWarning;

    /** sent=true si FCM aceptó el mensaje; si no, el motivo (máx. 120 caracteres) para auditoría. */
    public record PushResult(boolean sent,String error) {}

    @PostConstruct void init() { http=HttpClient.newBuilder().connectTimeout(config.httpTimeout()).build(); }

    public boolean enabled() { return config.enabled(); }
    public Duration timeout() { return config.httpTimeout(); }

    /**
     * Envía el payload del socket al topic. En `data` (solo texto) van `event`, `channel`, `recipientId`, `roomId` y el
     * payload completo como JSON en `payload`, que es lo que lee la app (CoordinatorPushService).
     *
     * @param ttl si FCM no lo entrega antes (teléfono apagado), lo descarta: el mensaje ya no sería actual.
     */
    public PushResult send(String topic,String event,UUID recipientId,Map<String,Object> payload,Duration ttl) {
        PushResult result;
        try {
            Map<String,String> data=new LinkedHashMap<>();
            data.put("event",event);
            data.put("channel",topic);
            data.put("recipientId",recipientId.toString());
            Object room=payload.get("roomId");
            if(room!=null) data.put("roomId",room.toString());
            data.put("payload",mapper.writeValueAsString(payload));
            Map<String,Object> android=Map.of("priority","HIGH","ttl",Math.max(1,ttl.toSeconds())+"s");
            String body=mapper.writeValueAsString(Map.of("message",Map.of("topic",topic,"data",data,"android",android)));
            String project=credentials.projectId();
            String token=credentials.accessToken();
            var request=HttpRequest.newBuilder(config.sendUri(project)).timeout(config.httpTimeout())
                .header("Authorization","Bearer "+token).header("Content-Type","application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            int code=response.statusCode();
            if(code>=200 && code<300) result=new PushResult(true,null);
            else {
                String status="";
                try { status=mapper.readTree(response.body()).path("error").path("status").asText(""); } catch(Exception ignored) {}
                result=new PushResult(false,limit("FCM_HTTP_"+code+(status.isBlank()?"":" "+status)));
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); result=new PushResult(false,"PUSH_INTERRUPTED");
        } catch(IllegalStateException e) {
            result=new PushResult(false,"PUSH_CREDENTIALS");
        } catch(Exception e) {
            result=new PushResult(false,"PUSH_UNAVAILABLE");
        }
        metrics.counter("sgi_push_send_total","result",result.sent()?"sent":"failed").increment();
        if(!result.sent()) warn(result.error());
        return result;
    }

    private static String limit(String value) { return value.substring(0,Math.min(120,value.length())); }

    private synchronized void warn(String reason) {
        long now=System.currentTimeMillis();
        if(now-lastWarning>=300_000) { lastWarning=now; LOG.warnf("Push FCM a SGI Operador no enviado: %s",reason); }
        else LOG.debugf("Push FCM no enviado: %s",reason);
    }

    @PreDestroy void close() { if(http!=null) http.shutdownNow(); }
}
