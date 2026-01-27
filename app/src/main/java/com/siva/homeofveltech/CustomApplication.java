package com.siva.homeofveltech;

import android.app.Application;
import android.content.Intent;

import com.siva.homeofveltech.services.SessionRenewalService;

public class CustomApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        startService(new Intent(this, SessionRenewalService.class));
    }
}
