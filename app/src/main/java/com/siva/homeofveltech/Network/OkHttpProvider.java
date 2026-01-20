package com.siva.homeofveltech.Network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class OkHttpProvider {

    private static OkHttpClient client;

    public static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .cookieJar(new SimpleCookieJar()) // ✅ keeps AMS session cookies
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(25, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .writeTimeout(25, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }
}
