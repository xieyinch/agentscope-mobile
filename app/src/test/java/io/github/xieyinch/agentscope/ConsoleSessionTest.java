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
        HeldCertificate cert=new HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").addSubjectAlternativeName("platform.agentscope.io").addSubjectAlternativeName("console.agentscope.io").build();
        HandshakeCertificates tls=new HandshakeCertificates.Builder().heldCertificate(cert).build();
        HandshakeCertificates trust=new HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate()).build();
        server=new MockWebServer();server.useHttps(tls.sslSocketFactory(),false);server.start();
        client=new OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager()).dns(host->Collections.singletonList(java.net.InetAddress.getByName("127.0.0.1"))).build();
    }
    @After public void tearDown() throws Exception {server.shutdown();}
    @Test public void exchangesSignedEntryForCookieBeforeHealthProbe() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Set-Cookie","gateway=fake-session; Path=/; Secure; HttpOnly").setHeader("Location","/console/chat"));
        server.enqueue(new MockResponse().setBody("<html>Console</html>"));
        server.enqueue(new MockResponse().setBody("{\"agents\":[]}"));
        ConsoleSession session=ConsoleSession.open(client,server.url("/console/chat?ticket=synthetic-ticket").toString(),"",true);
        RecordedRequest first=server.takeRequest(1,TimeUnit.SECONDS),landing=server.takeRequest(1,TimeUnit.SECONDS),probe=server.takeRequest(1,TimeUnit.SECONDS);
        assertEquals("/console/chat?ticket=synthetic-ticket",first.getPath());
        assertNull(first.getHeader("Authorization"));
        assertEquals("gateway=fake-session",landing.getHeader("Cookie"));
        assertEquals("/api/agents",probe.getPath());
        assertEquals("gateway=fake-session",probe.getHeader("Cookie"));
        assertEquals("",session.token);
    }
    @Test public void managedGatewayTokenIsNotMisusedAsRuntimeBearer() throws Exception {
        server.enqueue(new MockResponse().setHeader("Set-Cookie","gateway=test; Path=/; Secure; HttpOnly").setBody("console"));server.enqueue(new MockResponse().setBody("{\"agents\":[]}"));
        ConsoleSession.open(client,server.url("/chat?token=gateway-ticket").toString(),"",true);
        server.takeRequest();assertNull(server.takeRequest().getHeader("Authorization"));
    }
    @Test public void usesExplicitInstanceTokenOnlyForApiProbe() throws Exception {
        server.enqueue(new MockResponse().setBody("console"));server.enqueue(new MockResponse().setBody("{\"agents\":[]}"));
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
        assertEquals("/team/api/agents",base.newBuilder().addPathSegments("api/agents").build().encodedPath());
    }
    @Test public void carriesPlatformAccessResponseCookieIntoFirstConsoleNavigation() throws Exception {
        HttpUrl platform=server.url("/").newBuilder().host("platform.agentscope.io").build();
        HttpUrl console=server.url("/chat").newBuilder().host("console.agentscope.io").build();
        ConsoleSession.OriginCookies platformJar=new ConsoleSession.OriginCookies(platform);
        server.enqueue(new MockResponse().addHeader("Set-Cookie","gateway=synthetic-session; Domain=agentscope.io; Path=/; Secure; HttpOnly")
            .addHeader("Set-Cookie","platform_only=private-session; Path=/; Secure; HttpOnly").setBody("{\"data\":{}}"));
        server.enqueue(new MockResponse().setBody("console"));server.enqueue(new MockResponse().setBody("{\"agents\":[]}"));
        OkHttpClient platformClient=client.newBuilder().cookieJar(platformJar).build();
        try(Response response=platformClient.newCall(new Request.Builder().url(platform.resolve("/api/v1/app/access")).header("Authorization","Bearer synthetic-platform-token").build()).execute()){assertTrue(response.isSuccessful());}
        List<Cookie> inherited=ConsoleSession.managedEntryCookies(platformJar,console);assertEquals(1,inherited.size());
        ConsoleSession session=ConsoleSession.open(client,console.toString(),"",true,inherited);
        RecordedRequest access=server.takeRequest(),entry=server.takeRequest(),probe=server.takeRequest();
        assertEquals("Bearer synthetic-platform-token",access.getHeader("Authorization"));
        assertEquals("gateway=synthetic-session",entry.getHeader("Cookie"));
        assertEquals("gateway=synthetic-session",probe.getHeader("Cookie"));
        assertNull(entry.getHeader("Referer"));
        assertNull(entry.getHeader("Authorization"));assertNull(probe.getHeader("Authorization"));
        assertTrue(session.cookies.loadForRequest(platform).isEmpty());
    }
    @Test public void neverWidensHostOnlyPlatformCookiesToConsole() {
        HttpUrl platform=HttpUrl.get("https://platform.agentscope.io/");
        ConsoleSession.OriginCookies jar=new ConsoleSession.OriginCookies(platform);
        jar.saveFromResponse(platform,Collections.singletonList(Cookie.parse(platform,"private=value; Path=/; Secure; HttpOnly")));
        assertTrue(ConsoleSession.managedEntryCookies(jar,HttpUrl.get("https://console.agentscope.io/chat")).isEmpty());
    }
    @Test public void respectsCookiePathsAndRejectsUnrelatedConsoleHosts() {
        HttpUrl platform=HttpUrl.get("https://platform.agentscope.io/api/v1/app/access");
        ConsoleSession.OriginCookies jar=new ConsoleSession.OriginCookies(platform);
        jar.saveFromResponse(platform,Collections.singletonList(Cookie.parse(platform,"scoped=value; Domain=agentscope.io; Path=/api/; Secure; HttpOnly")));
        assertTrue(ConsoleSession.managedEntryCookies(jar,HttpUrl.get("https://console.agentscope.io/chat")).isEmpty());
        assertTrue(ConsoleSession.managedEntryCookies(jar,HttpUrl.get("https://agentscope.io.attacker.invalid/api/")).isEmpty());
        jar.clear();assertTrue(jar.loadForRequest(platform).isEmpty());
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
    @Test public void connectsWhenLegacyHealthRouteDoesNotExist() throws Exception {
        server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){
            if("/chat".equals(request.getPath()))return new MockResponse().setBody("console");
            if("/api/agents".equals(request.getPath()))return new MockResponse().setBody("{\"agents\":[{\"id\":\"default\"}]}");
            return new MockResponse().setResponseCode(404).setBody("{\"detail\":\"Not Found\"}");
        }});
        ConsoleSession.open(client,server.url("/chat").toString(),"");
        assertEquals("/chat",server.takeRequest().getPath());assertEquals("/api/agents",server.takeRequest().getPath());assertEquals(2,server.getRequestCount());
    }
    @Test public void refusesSuccessfulJsonFromWrongApi() throws Exception {
        server.enqueue(new MockResponse().setBody("console"));server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        try{ConsoleSession.open(client,server.url("/chat").toString(),"");fail();}catch(IOException e){assertTrue(e.getMessage().contains("非 Agent 列表"));}
    }
    @Test public void doesNotTreatInstanceUnauthorizedAsConnected() throws Exception {
        server.enqueue(new MockResponse().setBody("console"));server.enqueue(new MockResponse().setResponseCode(401));
        try{ConsoleSession.open(client,server.url("/chat").toString(),"");fail();}catch(IOException e){assertTrue(e.getMessage().contains("实例 401"));}
    }

}
