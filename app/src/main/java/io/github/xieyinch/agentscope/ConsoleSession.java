package io.github.xieyinch.agentscope;

import java.io.IOException;
import java.util.*;
import okhttp3.*;

/** Completes the platform's HTTPS access-link handshake before calling instance APIs. */
final class ConsoleSession {
    final HttpUrl base;
    final String token;
    final OriginCookies cookies;
    private ConsoleSession(HttpUrl base,String token,OriginCookies cookies){this.base=base;this.token=token;this.cookies=cookies;}

    static ConsoleSession open(OkHttpClient transport,String address,String suppliedToken) throws IOException {return open(transport,address,suppliedToken,false);}
    static ConsoleSession open(OkHttpClient transport,String address,String suppliedToken,boolean managed) throws IOException {
        HttpUrl entry=HttpUrl.parse(address.trim());
        if(entry==null||!entry.isHttps()||!entry.username().isEmpty()||!entry.password().isEmpty())
            throw new IOException("请使用有效的 HTTPS 实例地址");
        OriginCookies cookies=new OriginCookies(entry);
        OkHttpClient client=transport.newBuilder().cookieJar(cookies).followRedirects(false).followSslRedirects(false).build();
        HttpUrl current=entry;
        // This GET is essential: managed instances exchange their signed entry link for HttpOnly cookies.
        for(int redirect=0;;redirect++) {
            Request request=new Request.Builder().url(current).header("Accept","text/html,application/json").get().build();
            try(Response response=client.newCall(request).execute()) {
                int code=response.code();
                if(code==301||code==302||code==303||code==307||code==308){
                    if(redirect>=5)throw new IOException("控制台访问入口重定向过多，请重新连接");
                    String location=response.header("Location");HttpUrl next=location==null?null:current.resolve(location);
                    if(next==null||!sameOrigin(entry,next))throw new IOException("控制台入口转向了其他站点，已停止认证以保护凭据");
                    current=next;continue;
                }
                if(!response.isSuccessful())throw new IOException(code==401||code==403?
                    "控制台访问链接已失效，请返回部署页重新连接（入口认证 "+code+"）":"控制台访问入口失败（"+code+"）");
                break;
            }
        }
        String token=suppliedToken.trim();
        if(token.isEmpty()&&(!managed||cookies.serialize().isEmpty())){String query=entry.queryParameter("token");if(query!=null)token=query;}
        HttpUrl base=apiBase(current);
        Request.Builder verify=new Request.Builder().url(base.newBuilder().addPathSegments("api/agent/health").build()).get();
        if(!token.isEmpty())verify.header("Authorization","Bearer "+token);
        try(Response response=client.newCall(verify.build()).execute()){
            if(response.code()==401)throw new IOException("控制台入口已打开，但实例认证未通过（实例 401）。请重新获取平台访问链接，或填写实例访问令牌。");
            if(!response.isSuccessful())throw new IOException("实例连接检查失败（"+response.code()+"）");
            String probe=response.body()==null?"":response.body().string();
            if(probe.trim().startsWith("<"))throw new IOException("实例返回了访问链接失效页面，请返回部署页重新连接");
        }
        return new ConsoleSession(base,token,cookies);
    }
    static String platformAccessPath(String address) throws IOException {
        HttpUrl url=HttpUrl.get("https://platform.agentscope.io/").resolve(address);
        if(url==null||!url.encodedPath().startsWith("/api/"))throw new IOException("实例尚未就绪，请稍后刷新");
        return url.encodedPath()+(url.encodedQuery()==null?"":"?"+url.encodedQuery());
    }
    static boolean sameOrigin(HttpUrl a,HttpUrl b){return a.scheme().equals(b.scheme())&&a.host().equals(b.host())&&a.port()==b.port();}
    static HttpUrl apiBase(HttpUrl landing){
        String path=landing.encodedPath();
        int console=path.indexOf("/console");
        String prefix=console>=0?path.substring(0,console):"";
        return landing.newBuilder().encodedPath(prefix+"/").query(null).fragment(null).build();
    }
    static final class OriginCookies implements CookieJar {
        final HttpUrl origin;
        private final List<Cookie> values=new ArrayList<>();
        OriginCookies(HttpUrl origin){this.origin=origin;}
        @Override public synchronized void saveFromResponse(HttpUrl url,List<Cookie> incoming){
            if(!sameOrigin(origin,url))return;
            long now=System.currentTimeMillis();
            for(Cookie c:incoming){values.removeIf(old->old.name().equals(c.name())&&old.domain().equals(c.domain())&&old.path().equals(c.path()));if(c.expiresAt()>now)values.add(c);}
        }
        @Override public synchronized List<Cookie> loadForRequest(HttpUrl url){
            if(!sameOrigin(origin,url))return Collections.emptyList();
            values.removeIf(c->c.expiresAt()<=System.currentTimeMillis());
            List<Cookie> result=new ArrayList<>();for(Cookie c:values)if(c.matches(url))result.add(c);return result;
        }
        synchronized List<String> serialize(){List<String> out=new ArrayList<>();for(Cookie c:values)out.add(c.toString());return out;}
        static OriginCookies restore(HttpUrl origin,List<String> strings){OriginCookies jar=new OriginCookies(origin);List<Cookie> parsed=new ArrayList<>();for(String s:strings){Cookie c=Cookie.parse(origin,s);if(c!=null)parsed.add(c);}jar.saveFromResponse(origin,parsed);return jar;}
    }
}
