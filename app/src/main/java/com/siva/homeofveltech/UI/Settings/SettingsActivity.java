package com.siva.homeofveltech.UI.Settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;

import com.siva.homeofveltech.R;
import com.siva.homeofveltech.Storage.PrefsManager;
import com.siva.homeofveltech.UI.Login.LoginActivity;

public class SettingsActivity extends AppCompatActivity {

    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new PrefsManager(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvName = findViewById(R.id.tvStudentName);
        TextView tvBranch = findViewById(R.id.tvBranch);
        SwitchCompat swShowGrades = findViewById(R.id.swShowGrades);

        ImageView btnInstagram = findViewById(R.id.btnInstagram);
        ImageView btnWhatsapp = findViewById(R.id.btnWhatsapp);
        ImageView btnShare = findViewById(R.id.btnShare);

        AppCompatButton btnLogout = findViewById(R.id.btnLogout);

        String name = getIntent().getStringExtra("studentName");
        String branch = getIntent().getStringExtra("branch");

        if (TextUtils.isEmpty(name)) name = prefs.getStudentName();
        if (TextUtils.isEmpty(branch)) branch = prefs.getBranch();

        tvName.setText(TextUtils.isEmpty(name) ? "Student" : name);
        tvBranch.setText(TextUtils.isEmpty(branch) ? "Branch" : branch);

        swShowGrades.setChecked(prefs.isShowGrade());
        swShowGrades.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.setShowGrade(isChecked));

        btnBack.setOnClickListener(v -> finish());

        // social buttons (open browser)
        btnInstagram.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/"))));

        btnWhatsapp.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))));

        // share app
        btnShare.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Home of Veltech");
            share.putExtra(Intent.EXTRA_TEXT, "Try Home of Veltech app ✨");
            startActivity(Intent.createChooser(share, "Share via"));
        });

        btnLogout.setOnClickListener(v -> {
            prefs.clearAll();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }
}
