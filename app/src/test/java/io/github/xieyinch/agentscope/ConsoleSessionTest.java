package io.github.xieyinch.agentscope;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.*;
import static org.junit.Assert.*;

/** Regression: health APIs are unauthorized until the signed entry establishes its gateway session. */
public class ConsoleSessionTest {
    private MockWebServer server;
    private OkHttpClient client;
    @Before public void setUp() throws Exception {
        HeldCertificate cert=new HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build();
        HandshakeCertificates tls=new HandshakeCertificates.Builder().heldCertificate(cert).build();
        HandshakeCertificates trust=new HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate()).build();
        server=new MockWebServer();server.useHttps(tls.sslSocketFactory(),false);server.start();
        client=new OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager()).build();
    }
    @After public void tearDown() throws Exception {server.shutdown();}
    @Test public void exchangesSignedEntryForCookieBeforeHealthProbe() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Set-Cookie","gateway=fake-session; Path=/; Secure; HttpOnly").setHeader("Location","/console/chat"));
        server.enqueue(new MockResponse().setBody("<html>Console</html>"));
        server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        ConsoleSession session=ConsoleSession.open(client,server.url("/console/chat?ticket=synthetic-ticket").toString(),"",true);
        RecordedRequest first=server.takeRequest(1,TimeUnit.SECONDS),landing=server.takeRequest(1,TimeUnit.SECONDS),probe=server.takeRequest(1,TimeUnit.SECONDS);
        assertEquals("/console/chat?ticket=synthetic-ticket",first.getPath());
        assertNull(first.getHeader("Authorization"));
        assertEquals("gateway=fake-session",landing.getHeader("Cookie"));
        assertEquals("/api/agent/health",probe.getPath());
        assertEquals("gateway=fake-session",probe.getHeader("Cookie"));
        assertEquals("",session.token);
    }
    @Test public void managedGatewayTokenIsNotMisusedAsRuntimeBearer() throws Exception {
        server.enqueue(new MockResponse().setHeader("Set-Cookie","gateway=test; Path=/; Secure; HttpOnly").setBody("console"));server.enqueue(new MockResponse().setBody("{}"));
        ConsoleSession.open(client,server.url("/chat?token=gateway-ticket").toString(),"",true);
        server.takeRequest();assertNull(server.takeRequest().getHeader("Authorization"));
    }
    @Test public void usesExplicitInstanceTokenOnlyForApiProbe() throws Exception {
        server.enqueue(new MockResponse().setBody("console"));server.enqueue(new MockResponse().setBody("{}"));
        ConsoleSession.open(client,server.url("/console/chat").toString(),"synthetic-runtime-token");
        assertNull(server.takeRequest().getHeader("Authorization"));
        assertEquals("Bearer synthetic-runtime-token",server.takeRequest().getHeader("Authorization"));
    }
    @Test public void deniesCrossOriginRedirectWithoutContactingOtherHost() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location","https://other.invalid/collect"));
        try{ConsoleSession.open(client,server.url("/entry").toString(),"");fail("must reject cross origin");}catch(IOException e){assertTrue(e.getMessage().contains("其他站点"));}
        assertEquals(1,server.getRequestCount());
    }
    @Test public void restoresEncryptedStoreCookieRepresentationAndRestrictsOrigin() throws Exception {
        HttpUrl origin=server.url("/");ConsoleSession.OriginCookies jar=new ConsoleSession.OriginCookies(origin);
        jar.saveFromResponse(origin,Collections.singletonList(Cookie.parse(origin,"gateway=test; Path=/; Secure; HttpOnly")));
        ConsoleSession.OriginCookies restored=ConsoleSession.OriginCookies.restore(origin,jar.serialize());
        assertEquals(1,restored.loadForRequest(server.url("/api/chats")).size());
        assertTrue(restored.loadForRequest(HttpUrl.get("https://platform.agentscope.io/api/v1/app/list")).isEmpty());
        assertTrue(restored.loadForRequest(origin.newBuilder().port(origin.port()+1).build()).isEmpty());
    }
    @Test public void rejectsExpiredEntryAndDoesNotProbeHealth() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("expired"));
        try{ConsoleSession.open(client,server.url("/entry?ticket=expired").toString(),"");fail();}catch(IOException e){assertTrue(e.getMessage().contains("入口认证 401"));}
        assertEquals(1,server.getRequestCount());
    }
    @Test public void retainsReverseProxyPrefixAndDropsEntryQuery() {
        HttpUrl base=ConsoleSession.apiBase(HttpUrl.get("https://example.test/team/console/chat?ticket=secret"));
        assertEquals("https://example.test/team/",base.toString());
        assertEquals("/team/api/agent/health",base.newBuilder().addPathSegments("api/agent/health").build().encodedPath());
    }
    @Test public void preservesEncodedAccessPathAndSignedQueryExactly() throws Exception {
        assertEquals("/api/access/a%2Fb?signature=a%2Bb%2Fc%3D",ConsoleSession.platformAccessPath("https://platform.agentscope.io/api/access/a%2Fb?signature=a%2Bb%2Fc%3D"));
        assertEquals("/api/access?ticket=xyz",ConsoleSession.platformAccessPath("/api/access?ticket=xyz"));
    }
    @Test public void refusesNonApiAccessEntry() throws Exception {
        try{ConsoleSession.platformAccessPath("https://platform.agentscope.io/login");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("未就绪"));}
    }
    @Test public void doesNotAcceptAnHtmlErrorPageAsAHealthyApi() throws Exception {
        server.enqueue(new MockResponse().setBody("console"));server.enqueue(new MockResponse().setBody("<html>access expired</html>"));
        try{ConsoleSession.open(client,server.url("/chat").toString(),"");fail();}catch(IOException e){assertTrue(e.getMessage().contains("失效页面"));}
    }
}
