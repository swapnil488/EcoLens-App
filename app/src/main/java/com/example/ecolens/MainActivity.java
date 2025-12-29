package com.example.ecolens;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // --- Handle first install (fresh start after uninstall) ---
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean("first_launch", true);
        if (firstLaunch) {
            FirebaseAuth.getInstance().signOut();
            prefs.edit().putBoolean("first_launch", false).apply();
        }

        // --- Button Clicks ---
        findViewById(R.id.getStartedButton).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PollutionReporting.class))
        );

        findViewById(R.id.chat).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, Chat.class))
        );

        findViewById(R.id.readMore).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, DailyTips.class))
        );

        findViewById(R.id.ibProfile).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, Profile.class))
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            // No user logged in → go to GetStarted
            // navigateToGetStarted("Your session has ended. Please sign in again.");
        } else {
            // Validate token to ensure it's not expired or invalid (after reinstall, etc.)
            user.getIdToken(true).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    navigateToGetStarted("Session expired. Please sign in again.");
                }
            });
        }
    }

    private void navigateToGetStarted(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(MainActivity.this, GetStarted.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
