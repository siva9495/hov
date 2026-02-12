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

    // ✅ Results cache
    private static final String KEY_RESULTS_JSON = "results_json";
    private static final String KEY_RESULTS_UPDATED_AT = "results_updated_at";
    private static final String KEY_RESULTS_CGPA = "results_overall_cgpa";

    // ✅ Dashboard data cache
    private static final String KEY_DASHBOARD_JSON = "dashboard_json";
    private static final String KEY_DASHBOARD_UPDATED_AT = "dashboard_updated_at";

    // ✅ Attendance cache
    private static final String KEY_ATTENDANCE_JSON = "attendance_json";
    private static final String KEY_ATTENDANCE_UPDATED_AT = "attendance_updated_at";

    // ✅ Timetable cache
    private static final String KEY_TIMETABLE_JSON = "timetable_json";
    private static final String KEY_TIMETABLE_UPDATED_AT = "timetable_updated_at";

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
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
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

    public String getUsername() {
        return sp.getString(KEY_USER, "");
    }

    public String getPassword() {
        return sp.getString(KEY_PASS, "");
    }

    public String getStudentName() {
        return sp.getString(KEY_STUDENT_NAME, "");
    }

    public String getBranch() {
        return sp.getString(KEY_BRANCH, "");
    }

    public void setShowGrade(boolean show) {
        sp.edit().putBoolean(KEY_SHOW_GRADE, show).apply();
    }

    public boolean isShowGrade() {
        return sp.getBoolean(KEY_SHOW_GRADE, true);
    }

    public boolean hasCredentials() {
        return !getUsername().isEmpty() && !getPassword().isEmpty();
    }

    // ✅ Results cache helpers
    public void saveResultsCache(String resultsJson, double overallCgpa) {
        sp.edit()
                .putString(KEY_RESULTS_JSON, resultsJson == null ? "" : resultsJson)
                .putLong(KEY_RESULTS_UPDATED_AT, System.currentTimeMillis())
                .putFloat(KEY_RESULTS_CGPA, (float) overallCgpa)
                .apply();
    }

    public String getResultsCacheJson() {
        return sp.getString(KEY_RESULTS_JSON, "");
    }

    public long getResultsCacheUpdatedAt() {
        return sp.getLong(KEY_RESULTS_UPDATED_AT, 0L);
    }

    public double getResultsCacheCgpa() {
        return sp.getFloat(KEY_RESULTS_CGPA, 0f);
    }

    public boolean hasResultsCache() {
        String j = getResultsCacheJson();
        return j != null && !j.trim().isEmpty();
    }

    public void clearResultsCache() {
        sp.edit()
                .remove(KEY_RESULTS_JSON)
                .remove(KEY_RESULTS_UPDATED_AT)
                .remove(KEY_RESULTS_CGPA)
                .apply();
    }

    public void clearAll() {
        sp.edit().clear().apply();
    }

    // ✅ Dashboard cache
    public void saveDashboardCache(String dashboardJson) {
        sp.edit()
                .putString(KEY_DASHBOARD_JSON, dashboardJson == null ? "" : dashboardJson)
                .putLong(KEY_DASHBOARD_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    public String getDashboardCache() {
        return sp.getString(KEY_DASHBOARD_JSON, "");
    }

    public long getDashboardCacheUpdatedAt() {
        return sp.getLong(KEY_DASHBOARD_UPDATED_AT, 0L);
    }

    public boolean hasDashboardCache() {
        String j = getDashboardCache();
        return j != null && !j.trim().isEmpty();
    }

    // ✅ Attendance cache
    public void saveAttendanceCache(String attendanceJson) {
        sp.edit()
                .putString(KEY_ATTENDANCE_JSON, attendanceJson == null ? "" : attendanceJson)
                .putLong(KEY_ATTENDANCE_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    public String getAttendanceCache() {
        return sp.getString(KEY_ATTENDANCE_JSON, "");
    }

    public long getAttendanceCacheUpdatedAt() {
        return sp.getLong(KEY_ATTENDANCE_UPDATED_AT, 0L);
    }

    public boolean hasAttendanceCache() {
        String j = getAttendanceCache();
        return j != null && !j.trim().isEmpty();
    }

    // ✅ Timetable cache
    public void saveTimetableCache(String timetableJson) {
        sp.edit()
                .putString(KEY_TIMETABLE_JSON, timetableJson == null ? "" : timetableJson)
                .putLong(KEY_TIMETABLE_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    public String getTimetableCache() {
        return sp.getString(KEY_TIMETABLE_JSON, "");
    }

    public long getTimetableCacheUpdatedAt() {
        return sp.getLong(KEY_TIMETABLE_UPDATED_AT, 0L);
    }

    public boolean hasTimetableCache() {
        String j = getTimetableCache();
        return j != null && !j.trim().isEmpty();
    }
}
