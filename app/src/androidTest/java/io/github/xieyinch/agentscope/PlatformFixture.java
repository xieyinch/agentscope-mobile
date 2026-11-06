package io.github.xieyinch.agentscope;

import okhttp3.OkHttpClient;

/** Installs credential-free transport for shell/console integration tests only. */
public final class PlatformFixture {
    public static void bind(MainActivity activity,OkHttpClient client) throws Exception {
        SecretStore store=new SecretStore(activity);store.put("instance","https://console-fixture.invalid");store.put("instanceToken","fixture-instance-token");
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("api");field.setAccessible(true);Api api=(Api)field.get(activity);
        java.lang.reflect.Field http=Api.class.getDeclaredField("http");http.setAccessible(true);http.set(api,client);
    }
    public static void clear(MainActivity activity){new SecretStore(activity).clear();}
}
