package com.siva.homeofveltech.Storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class PrefsManager {

    private static final String FILE = "home_of_veltech_secure";

    private static final String KEY_USER = "username";
    private static final String KEY_PASS = "password";
    private static final String KEY_STUDENT_NAME = "studentName";
    private static final String KEY_BRANCH = "branch";
    private static final String KEY_SHOW_GRADE = "showGrade";

    private final SharedPreferences sp;

    public PrefsManager(Context ctx) {
        SharedPreferences temp;
        try {
            MasterKey masterKey = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            temp = EncryptedSharedPreferences.create(
                    ctx,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            temp = ctx.getSharedPreferences("home_of_veltech_plain", Context.MODE_PRIVATE);
        }
        sp = temp;
    }

    public void saveCredentials(String username, String password) {
        sp.edit().putString(KEY_USER, username).putString(KEY_PASS, password).apply();
    }

    public void saveStudentProfile(String studentName, String branch) {
        sp.edit()
                .putString(KEY_STUDENT_NAME, studentName == null ? "" : studentName)
                .putString(KEY_BRANCH, branch == null ? "" : branch)
                .apply();
    }

    public String getUsername() { return sp.getString(KEY_USER, ""); }
    public String getPassword() { return sp.getString(KEY_PASS, ""); }

    public String getStudentName() { return sp.getString(KEY_STUDENT_NAME, ""); }
    public String getBranch() { return sp.getString(KEY_BRANCH, ""); }

    public void setShowGrade(boolean show) { sp.edit().putBoolean(KEY_SHOW_GRADE, show).apply(); }
    public boolean isShowGrade() { return sp.getBoolean(KEY_SHOW_GRADE, true); }

    public boolean hasCredentials() {
        return !getUsername().isEmpty() && !getPassword().isEmpty();
    }

    public void clearAll() {
        sp.edit().clear().apply();
    }
}
