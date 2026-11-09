package com.cajamarca.sgi.comando.socket;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SocketConfigTest {
    @Test void urlsYCanalesDelGatewayReal() {
        SocketConfig c=new SocketConfig(); c.url="https://apps.cajamarca.ec/dev.socket/"; c.channelPrefix="persona.";
        assertEquals("https://apps.cajamarca.ec/dev.socket",c.baseUrl());
        assertEquals(c.baseUrl()+"/api/v1/publish",c.publishUri().toString());
        assertEquals(c.baseUrl()+"/q/health/ready",c.healthUri().toString());
        assertEquals("persona.9428",c.channelFor(9428));
        assertTrue(c.presenceUri("persona.9428: a").toString().endsWith("persona.9428%3A+a"));
        for(String url:List.of("wss://localhost/ws","https://localhost/ws?channels=x","https://u:p@localhost/ws","https://localhost/ws#x")) {
            c.url=url; assertThrows(IllegalStateException.class,c::baseUrl);
        }
    }
    @Test void origenRealAdmiteIpsYCidrsSinDns() {
        assertTrue(SocketConfig.matchesAddress("127.0.0.1","127.0.0.1"));
        assertTrue(SocketConfig.matchesAddress("172.18.5.10","172.18.0.0/16"));
        assertFalse(SocketConfig.matchesAddress("172.19.5.10","172.18.0.0/16"));
        assertTrue(SocketConfig.matchesAddress("::1","::1/128"));
        for(String invalid:List.of("localhost","0.0.0.0/999","127.0.0.1/-1","127.0.0.1/a")) assertFalse(SocketConfig.matchesAddress("127.0.0.1",invalid));
        SocketConfig c=new SocketConfig();c.allowedIps=Optional.of("127.0.0.1/32, ::1/128");
        assertTrue(c.allowsRemote("127.0.0.1"));assertFalse(c.allowsRemote("192.0.2.1"));
    }
}
