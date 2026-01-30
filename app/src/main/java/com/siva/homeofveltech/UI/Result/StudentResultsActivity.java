package com.siva.homeofveltech.UI.Result;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.siva.homeofveltech.Adapter.SemesterResultAdapter;
import com.siva.homeofveltech.Model.SemesterResult;
import com.siva.homeofveltech.Network.AmsClient;
import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudentResultsActivity extends AppCompatActivity {

    private ShimmerFrameLayout shimmerLayout;
    private View contentContainer;

    private TextView txtOverallCgpa;
    private RecyclerView rvSemesters;

    private SemesterResultAdapter semesterAdapter;

    private PrefsManager prefs;
    private AmsClient amsClient;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<SemesterResult>>(){}.getType();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_results);

        prefs = new PrefsManager(this);
        amsClient = new AmsClient();

        shimmerLayout = findViewById(R.id.shimmer_layout);
        contentContainer = findViewById(R.id.content_container);

        TextView txtTitle = findViewById(R.id.txt_page_title);
        ImageView btnInfo = findViewById(R.id.btn_info);

        txtOverallCgpa = findViewById(R.id.txt_overall_cgpa);
        rvSemesters = findViewById(R.id.rv_semesters);

        txtTitle.setText("Results");

        rvSemesters.setLayoutManager(new LinearLayoutManager(this));
        semesterAdapter = new SemesterResultAdapter(new ArrayList<>(), prefs.isShowGrade());
        rvSemesters.setAdapter(semesterAdapter);

        btnInfo.setOnClickListener(v -> showCgpaInfoDialog());

        // ✅ 1) Show cached instantly (if present)
        boolean showedCache = tryShowCachedResults();

        // ✅ 2) If no cache -> shimmer, else keep content visible and refresh silently
        if (!showedCache) setLoading(true);

        // ✅ 3) Load fresh results
        loadRealResults();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // if user toggled showGrade in settings, reflect immediately
        semesterAdapter.setShowGrade(prefs.isShowGrade());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private boolean tryShowCachedResults() {
        try {
            if (!prefs.hasResultsCache()) return false;

            String json = prefs.getResultsCacheJson();
            List<SemesterResult> cached = gson.fromJson(json, listType);
            if (cached == null || cached.isEmpty()) return false;

            semesterAdapter.setItems(cached);

            double overall = prefs.getResultsCacheCgpa();
            if (overall <= 0) overall = computeOverallCgpa(cached);
            txtOverallCgpa.setText(String.format(Locale.US, "%.2f", overall));

            // show content (no shimmer)
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);

            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void loadRealResults() {
        executor.execute(() -> {
            try {
                ensureLoggedIn();

                // ✅ FIX: your AmsClient needs ResultType argument
                List<SemesterResult> fresh =
                        amsClient.fetchAllSemesterResults(AmsClient.ResultType.REGULAR);

                if (fresh == null) fresh = new ArrayList<>();

                double overall = computeOverallCgpa(fresh);

                String json = gson.toJson(fresh, listType);
                prefs.saveResultsCache(json, overall);

                List<SemesterResult> finalFresh = fresh;
                mainHandler.post(() -> {
                    semesterAdapter.setItems(finalFresh);
                    txtOverallCgpa.setText(String.format(Locale.US, "%.2f", overall));
                    setLoading(false);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (prefs.hasResultsCache()) {
                        Toast.makeText(this, "Showing saved results (refresh failed)", Toast.LENGTH_SHORT).show();
                        setLoading(false);
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Failed to load results", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void ensureLoggedIn() throws Exception {
        // If session valid, ok
        if (amsClient.isSessionValid()) return;

        // Try re-login using saved credentials
        if (!prefs.hasCredentials()) throw new Exception("No credentials saved");

        boolean ok = amsClient.login(prefs.getUsername(), prefs.getPassword());
        if (!ok) throw new Exception("Login failed");
    }

    private void setLoading(boolean loading) {
        if (loading) {
            shimmerLayout.startShimmer();
            shimmerLayout.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
        } else {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
        }
    }

    private double computeOverallCgpa(List<SemesterResult> list) {
        if (list == null || list.isEmpty()) return 0.0;
        double sum = 0;
        int count = 0;
        for (SemesterResult s : list) {
            if (s == null) continue;
            sum += s.tgpa;
            count++;
        }
        return count == 0 ? 0.0 : (sum / count);
    }

    private void setBlurBackground(boolean enable) {
        View root = findViewById(android.R.id.content);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enable) root.setRenderEffect(RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP));
            else root.setRenderEffect(null);
        } else {
            root.setAlpha(enable ? 0.96f : 1f);
        }
    }

    private void showCgpaInfoDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_results_info, null);

        setBlurBackground(true);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ResultsInfoDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setGravity(Gravity.CENTER);
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setDimAmount(0.35f);

                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
                w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            }

            dialogView.setAlpha(0f);
            dialogView.setScaleX(0.96f);
            dialogView.setScaleY(0.96f);
            dialogView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });

        dialog.setOnDismissListener(d -> setBlurBackground(false));

        View btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
