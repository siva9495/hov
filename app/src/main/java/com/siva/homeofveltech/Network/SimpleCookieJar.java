package com.siva.homeofveltech.Network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class SimpleCookieJar implements CookieJar {

    private final List<Cookie> cookieStore = new ArrayList<>();

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        for (Cookie newCookie : cookies) {
            // remove old cookie with same (name, domain, path)
            Iterator<Cookie> it = cookieStore.iterator();
            while (it.hasNext()) {
                Cookie old = it.next();
                if (old.name().equals(newCookie.name())
                        && old.domain().equals(newCookie.domain())
                        && old.path().equals(newCookie.path())) {
                    it.remove();
                }
            }
            cookieStore.add(newCookie);
        }
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> valid = new ArrayList<>();
        long now = System.currentTimeMillis();

        Iterator<Cookie> it = cookieStore.iterator();
        while (it.hasNext()) {
            Cookie c = it.next();

            // drop expired
            if (c.expiresAt() < now) {
                it.remove();
                continue;
            }

            // only send cookies that match this request URL
            if (c.matches(url)) valid.add(c);
        }
        return valid;
    }

    public synchronized void clear() {
        cookieStore.clear();
    }
}
