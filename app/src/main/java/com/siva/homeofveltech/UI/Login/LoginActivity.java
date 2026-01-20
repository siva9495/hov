package com.siva.homeofveltech.UI.Login;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.siva.homeofveltech.Model.StudentProfile;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.Dashboard.DashboardActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private ImageView ivEyeIcon;
    private AppCompatButton btnLogin;

    private boolean isPasswordVisible = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AmsClient amsClient = new AmsClient();

    private PrefsManager prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = new PrefsManager(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        ivEyeIcon  = findViewById(R.id.ivEyeIcon);
        btnLogin   = findViewById(R.id.btnLogin);

        if (prefs.hasCredentials()) {
            etUsername.setText(prefs.getUsername());
            etPassword.setText(prefs.getPassword());
        }

        ivEyeIcon.setOnClickListener(v -> togglePasswordVisibility());

        btnLogin.setOnClickListener(v -> {
            hideKeyboard();

            String username = etUsername.getText() == null ? "" : etUsername.getText().toString().trim();
            String password = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();

            if (username.isEmpty()) { etUsername.setError("Enter Username"); etUsername.requestFocus(); return; }
            if (password.isEmpty()) { etPassword.setError("Enter Password"); etPassword.requestFocus(); return; }

            setLoading(true);

            executor.execute(() -> {
                try {
                    boolean ok = amsClient.login(username, password);

                    StudentProfile profile = new StudentProfile("", "");
                    if (ok) {
                        try { profile = amsClient.fetchStudentProfile(); } catch (Exception ignored) {}
                    }

                    StudentProfile finalProfile = profile;

                    runOnUiThread(() -> {
                        setLoading(false);

                        if (ok) {
                            prefs.saveCredentials(username, password);
                            prefs.saveStudentProfile(finalProfile.studentName, finalProfile.branch);

                            Toast.makeText(this, "Login Success ✅", Toast.LENGTH_SHORT).show();

                            Intent i = new Intent(this, DashboardActivity.class);
                            i.putExtra("username", username);
                            i.putExtra("studentName", finalProfile.studentName);
                            i.putExtra("branch", finalProfile.branch);
                            startActivity(i);
                            finish();
                        } else {
                            Toast.makeText(this, "Invalid Username / Password", Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(this, "Server error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
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

        if (etPassword.getText() != null) etPassword.setSelection(etPassword.getText().length());
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setAlpha(loading ? 0.75f : 1f);
        btnLogin.setText(loading ? "Logging in..." : "Log In");

        etUsername.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        ivEyeIcon.setEnabled(!loading);
    }

    private void hideKeyboard() {
        try {
            View v = getCurrentFocus();
            if (v == null) return;
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
