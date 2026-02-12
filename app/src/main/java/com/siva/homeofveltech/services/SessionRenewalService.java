package com.siva.homeofveltech.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * This service was originally designed to renew the AMS session in the background.
 * With the introduction of a captcha on the login page, silent re-authentication is no longer possible.
 * The session is now checked before each network request in the UI layer (Activities),
 * and the user is redirected to the login screen if the session is expired.
 * <p>
 * This service is now deprecated and will stop itself immediately if started.
 */
public class SessionRenewalService extends Service {

    private static final String TAG = "SessionRenewalService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created. This service is deprecated and will be stopped.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.w(TAG, "SessionRenewalService is no longer functional due to captcha. Stopping service.");
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Session Renewal Service stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
