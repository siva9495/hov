package com.siva.homeofveltech.services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionRenewalService extends Service {

    private static final String TAG = "SessionRenewalService";
    private static final long RENEWAL_INTERVAL = 10 * 60 * 1000; // 10 minutes

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PrefsManager prefsManager;
    private AmsClient amsClient;

    private final Runnable renewalRunnable = new Runnable() {
        @Override
        public void run() {
            renewSession();
            handler.postDelayed(this, RENEWAL_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefsManager = new PrefsManager(this);
        amsClient = new AmsClient();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Session Renewal Service started");
        handler.removeCallbacks(renewalRunnable);
        handler.post(renewalRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(renewalRunnable);
        executor.shutdownNow();
        Log.d(TAG, "Session Renewal Service stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void renewSession() {
        if (!prefsManager.hasCredentials()) return;

        executor.execute(() -> {
            try {
                String username = prefsManager.getUsername();
                String password = prefsManager.getPassword();

                boolean loggedIn = amsClient.login(username, password);
                if (loggedIn) Log.d(TAG, "Session renewed successfully");
                else Log.e(TAG, "Failed to renew session");

            } catch (Exception e) {
                Log.e(TAG, "Error renewing session", e);
            }
        });
    }
}
