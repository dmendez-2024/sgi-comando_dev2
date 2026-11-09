package com.cajamarca.sgi.comando.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Publicación REST directa al gateway; ninguna conexión persistente desde Comando. */
@ApplicationScoped
public class SocketPublisher {
    private static final Logger LOG=Logger.getLogger(SocketPublisher.class);
    @Inject SocketConfig config;
    @Inject ObjectMapper mapper;
    @Inject MeterRegistry metrics;
    private HttpClient http;
    private final AtomicInteger lastOk=new AtomicInteger();
    private long lastWarning;
    public enum Outcome { ACCEPTED, REJECTED, UNAVAILABLE }
    public record PublishResult(Outcome outcome,Integer localRecipients,String reason) {}
    @PostConstruct void init() {
        http=HttpClient.newBuilder().connectTimeout(config.httpTimeout()).build();
        metrics.gauge("sgi_socket_last_publish_ok",lastOk);
    }
    public boolean lastPublishOk() { return lastOk.get()==1; }
    public PublishResult publish(UUID id,String channel,Object payload) {
        PublishResult result;
        try {
            String body=mapper.writeValueAsString(Map.of("id",id,"source",config.source(),"channel",channel,"event",config.event(),"payload",payload));
            var request=HttpRequest.newBuilder(config.publishUri()).timeout(config.httpTimeout())
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            int code=response.statusCode();
            if(code>=200 && code<300) {
                Integer count=null;
                try { var n=mapper.readTree(response.body()).path("localRecipients"); if(n.canConvertToInt()) count=n.intValue(); }
                catch(Exception ignored) { /* El gateway puede aceptar sin devolver contadores. */ }
                result=new PublishResult(Outcome.ACCEPTED,count,null);
            } else if(code==400) {
                String error="Solicitud inválida";
                try { error=mapper.readTree(response.body()).path("error").asText(error); } catch(Exception ignored) {}
                // Solo persiste el motivo de rechazo; nunca se registra el body en logs.
                result=new PublishResult(Outcome.REJECTED,null,"SOCKET_400: "+error.substring(0,Math.min(150,error.length())));
            } else result=new PublishResult(Outcome.UNAVAILABLE,null,"SOCKET_HTTP_"+code);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); result=new PublishResult(Outcome.UNAVAILABLE,null,"SOCKET_INTERRUPTED");
        } catch(Exception e) { result=new PublishResult(Outcome.UNAVAILABLE,null,"SOCKET_UNAVAILABLE"); }
        lastOk.set(result.outcome()==Outcome.ACCEPTED?1:0);
        metrics.counter("sgi_socket_publish_total","result",result.outcome().name().toLowerCase(Locale.ROOT)).increment();
        if(result.outcome()==Outcome.UNAVAILABLE) warnUnavailable();
        return result;
    }
    private synchronized void warnUnavailable() {
        long now=System.currentTimeMillis();
        if(now-lastWarning>=300_000) { lastWarning=now; LOG.warn("Gateway del socket no disponible; los mensajes se reintentarán"); }
        else LOG.debug("Publicación al gateway pendiente");
    }
    @PreDestroy void close() { if(http!=null) http.shutdownNow(); }
}
