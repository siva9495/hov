package com.siva.homeofveltech.UI.Dialog;

import android.app.Dialog;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialog for refreshing user session with captcha verification.
 * Reuses the captcha flow from LoginActivity for session renewal without full
 * logout.
 */
public class SessionRefreshDialog extends DialogFragment {

    private ImageView ivCaptcha, ivRefreshCaptcha;
    private EditText etCaptcha;
    private MaterialButton btnRefresh;
    private View progressOverlay;

    private AmsClient dialogAmsClient; // Separate instance to avoid session interference
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PrefsManager prefs;
    private Map<String, String> hiddenFields;

    private RefreshCallback callback;

    /**
     * Callback interface for session refresh results
     */
    public interface RefreshCallback {
        void onRefreshSuccess();

        void onRefreshFailed(String error);
    }

    public void setCallback(RefreshCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogTheme);
        prefs = new PrefsManager(requireContext());
        // Create separate AmsClient instance to avoid interfering with parent session
        dialogAmsClient = new AmsClient();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_session_refresh, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivCaptcha = view.findViewById(R.id.ivCaptcha);
        ivRefreshCaptcha = view.findViewById(R.id.ivRefreshCaptcha);
        etCaptcha = view.findViewById(R.id.etCaptcha);
        btnRefresh = view.findViewById(R.id.btnRefresh);
        progressOverlay = view.findViewById(R.id.progressOverlay);

        ivRefreshCaptcha.setOnClickListener(v -> fetchCaptcha());
        btnRefresh.setOnClickListener(v -> submitRefresh());

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        fetchCaptcha();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void fetchCaptcha() {
        setLoading(true);
        executor.execute(() -> {
            try {
                // Use separate AmsClient to fetch captcha without affecting parent session
                AmsClient.LoginPageData pageData = dialogAmsClient.fetchLoginPage();
                hiddenFields = pageData.hiddenFields;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        ivCaptcha.setImageBitmap(BitmapFactory.decodeByteArray(
                                pageData.captchaImage, 0, pageData.captchaImage.length));
                        etCaptcha.setText("");
                        setLoading(false);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Failed to load captcha: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        setLoading(false);
                    });
                }
            }
        });
    }

    private void submitRefresh() {
        String captcha = etCaptcha.getText() == null ? "" : etCaptcha.getText().toString().trim();

        if (captcha.isEmpty()) {
            etCaptcha.setError("Enter Captcha");
            etCaptcha.requestFocus();
            return;
        }

        String username = prefs.getUsername();
        String password = prefs.getPassword();

        if (username.isEmpty() || password.isEmpty()) {
            if (callback != null) {
                callback.onRefreshFailed("Credentials not found. Please login again.");
            }
            dismiss();
            return;
        }

        setLoading(true);

        executor.execute(() -> {
            try {
                // Use dialog's AmsClient for login
                boolean success = dialogAmsClient.login(username, password, captcha, hiddenFields);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        setLoading(false);

                        if (success) {
                            if (callback != null) {
                                callback.onRefreshSuccess();
                            }
                            dismiss();
                        } else {
                            Toast.makeText(getContext(), "Invalid captcha. Please try again.",
                                    Toast.LENGTH_LONG).show();
                            fetchCaptcha(); // Refresh captcha on failure
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        setLoading(false);
                        if (callback != null) {
                            callback.onRefreshFailed("Failed: " + e.getMessage());
                        }
                        fetchCaptcha(); // Refresh captcha on error
                    });
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        if (progressOverlay != null) {
            progressOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnRefresh != null) {
            btnRefresh.setEnabled(!loading);
            btnRefresh.setAlpha(loading ? 0.6f : 1f);
        }
        if (etCaptcha != null) {
            etCaptcha.setEnabled(!loading);
        }
        if (ivRefreshCaptcha != null) {
            ivRefreshCaptcha.setEnabled(!loading);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
