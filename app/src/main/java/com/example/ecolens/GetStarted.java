package com.example.ecolens;

import android.content.Intent;
import androidx.activity.EdgeToEdge;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.auth.FirebaseAuth;

public class GetStarted extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_get_started);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // If the user is already logged in, redirect them to the Home page
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(GetStarted.this, MainActivity.class));
            finish();
        }

        Button getStartedButton = findViewById(R.id.get_started);
        getStartedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Redirect to Login activity
                startActivity(new Intent(GetStarted.this, LoginActivity.class));
            }
        });
    }
}
