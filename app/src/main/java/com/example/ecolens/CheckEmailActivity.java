package com.example.ecolens;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CheckEmailActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private String email;
    private Handler handler = new Handler();
    private Runnable checkTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_check_email);

        // insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        email = getIntent().getStringExtra("email");

        // 1) create temp account
        mAuth.createUserWithEmailAndPassword(email, "TempPass#123")
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this,
                                "Error creating temp user: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    currentUser = mAuth.getCurrentUser();
                    if (currentUser == null) return;

                    // 2) send verification email
                    currentUser.sendEmailVerification()
                            .addOnCompleteListener(sent -> {
                                if (sent.isSuccessful()) {
                                    Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show();
                                    startPolling();
                                } else {
                                    Toast.makeText(this,
                                            "Could not send verification: " + sent.getException().getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                });
    }

    private void startPolling() {
        checkTask = new Runnable() {
            @Override
            public void run() {
                currentUser.reload().addOnCompleteListener(t -> {
                    if (currentUser.isEmailVerified()) {
                        // move back to SignUpActivity
                        Intent i = new Intent(CheckEmailActivity.this, SignUpActivity.class);
                        i.putExtra("emailVerified", true);
                        i.putExtra("verifiedEmail", email);
                        startActivity(i);
                        finish();
                    } else {
                        handler.postDelayed(this, 3000);
                    }
                });
            }
        };
        handler.post(checkTask);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(checkTask);
        super.onDestroy();
    }
}
