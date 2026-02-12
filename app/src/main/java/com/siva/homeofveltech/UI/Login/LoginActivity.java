package com.siva.homeofveltech.UI.Login;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.siva.homeofveltech.Model.StudentProfile;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.SubjectAttendanceItem;
import com.siva.homeofveltech.Model.SemesterResult;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.Dashboard.DashboardActivity;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etCaptcha;
    private ImageView ivEyeIcon, ivCaptcha, ivRefreshCaptcha;
    private MaterialButton btnLogin;

    private boolean isPasswordVisible = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();

    private PrefsManager prefs;
    private Map<String, String> hiddenFields;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = new PrefsManager(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etCaptcha = findViewById(R.id.etCaptcha);
        ivEyeIcon = findViewById(R.id.ivEyeIcon);
        ivCaptcha = findViewById(R.id.ivCaptcha);
        ivRefreshCaptcha = findViewById(R.id.ivRefreshCaptcha);
        btnLogin = findViewById(R.id.btnLogin);

        if (prefs.hasCredentials()) {
            etUsername.setText(prefs.getUsername());
            etPassword.setText(prefs.getPassword());
        }

        ivEyeIcon.setOnClickListener(v -> togglePasswordVisibility());
        ivRefreshCaptcha.setOnClickListener(v -> fetchCaptcha());

        btnLogin.setOnClickListener(v -> {
            hideKeyboard();

            String username = etUsername.getText() == null ? "" : etUsername.getText().toString().trim();
            String password = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();
            String captcha = etCaptcha.getText() == null ? "" : etCaptcha.getText().toString().trim();

            if (username.isEmpty()) {
                etUsername.setError("Enter Username");
                etUsername.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                etPassword.setError("Enter Password");
                etPassword.requestFocus();
                return;
            }
            if (captcha.isEmpty()) {
                etCaptcha.setError("Enter Captcha");
                etCaptcha.requestFocus();
                return;
            }

            setLoading(true);

            executor.execute(() -> {
                try {
                    boolean ok = amsClient.login(username, password, captcha, hiddenFields);

                    StudentProfile profile = new StudentProfile("", "");
                    if (ok) {
                        try {
                            profile = amsClient.fetchStudentProfile();
                        } catch (Exception ignored) {
                        }
                    }

                    StudentProfile finalProfile = profile;

                    runOnUiThread(() -> {
                        setLoading(false);

                        if (ok) {
                            prefs.saveCredentials(username, password);
                            prefs.saveStudentProfile(finalProfile.studentName, finalProfile.branch);

                            // Fetch and cache ALL data on first login
                            fetchAndCacheAllData();

                            Toast.makeText(this, "Login Success ✅", Toast.LENGTH_SHORT).show();

                            Intent i = new Intent(this, DashboardActivity.class);
                            i.putExtra("username", username);
                            i.putExtra("studentName", finalProfile.studentName);
                            i.putExtra("branch", finalProfile.branch);
                            startActivity(i);
                            finish();
                        } else {
                            Toast.makeText(this, "Invalid Credentials or Captcha", Toast.LENGTH_LONG).show();
                            fetchCaptcha(); // Refresh captcha on failure
                        }
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(this, "Server error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        fetchCaptcha(); // Refresh captcha on error
                    });
                }
            });
        });

        fetchCaptcha();
    }

    private void fetchCaptcha() {
        setLoading(true);
        executor.execute(() -> {
            try {
                AmsClient.LoginPageData pageData = amsClient.fetchLoginPage();
                hiddenFields = pageData.hiddenFields;
                runOnUiThread(() -> {
                    ivCaptcha.setImageBitmap(
                            BitmapFactory.decodeByteArray(pageData.captchaImage, 0, pageData.captchaImage.length));
                    etCaptcha.setText("");
                    setLoading(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to load captcha: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    setLoading(false);
                });
            }
        });
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivEyeIcon.setImageResource(R.drawable.ic_eye_open);
        } else {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivEyeIcon.setImageResource(R.drawable.ic_eye_closed);
        }

        if (etPassword.getText() != null)
            etPassword.setSelection(etPassword.getText().length());
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setAlpha(loading ? 0.75f : 1f);
        btnLogin.setText(loading ? "Logging in..." : "Log In");

        etUsername.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etCaptcha.setEnabled(!loading);
        ivEyeIcon.setEnabled(!loading);
        ivRefreshCaptcha.setEnabled(!loading);
    }

    private void hideKeyboard() {
        try {
            View v = getCurrentFocus();
            if (v == null)
                return;
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    /**
     * Fetch all user data and cache it for instant loading
     * Runs in background, doesn't block login flow
     */
    private void fetchAndCacheAllData() {
        ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor();
        prefetchExecutor.execute(() -> {
            Gson gson = new Gson();
            try {
                // Fetch dashboard data
                StudentDashboardData dashboardData = amsClient.fetchStudentDashboardData();
                String dashboardJson = gson.toJson(dashboardData);
                prefs.saveDashboardCache(dashboardJson);
                prefs.saveTimetableCache(gson.toJson(dashboardData.weekTimetable));
                prefs.saveStudentProfile(dashboardData.studentName, dashboardData.branch);
            } catch (Exception ignored) {
                // keep going to cache whatever else is available
            }

            try {
                // Fetch attendance data
                List<SubjectAttendanceItem> attendanceData = amsClient.fetchAttendanceData();
                String attendanceJson = gson.toJson(attendanceData);
                prefs.saveAttendanceCache(attendanceJson);
            } catch (Exception ignored) {
                // keep going
            }

            try {
                // Fetch results data
                List<SemesterResult> resultsData = amsClient.fetchAllSemesterResultsRegular();
                double cgpa = 0.0;
                if (!resultsData.isEmpty() && resultsData.get(resultsData.size() - 1).tgpa > 0) {
                    cgpa = resultsData.get(resultsData.size() - 1).tgpa;
                }
                String resultsJson = gson.toJson(resultsData);
                prefs.saveResultsCache(resultsJson, cgpa);
            } catch (Exception ignored) {
                // keep going
            } finally {
                prefetchExecutor.shutdown();
            }
        });
    }
}
