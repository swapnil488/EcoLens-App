package com.example.ecolens;

import android.app.ProgressDialog;
import android.content.Intent;
import androidx.activity.EdgeToEdge;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn, btnForgotPassword, btnSignUp;
    private ImageButton btnClose, btnTogglePwd;
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;
    private boolean pwdVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Find views from the XML layout
        btnClose = findViewById(R.id.btnClose);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnTogglePwd = findViewById(R.id.btnTogglePwd);

        // Set listener on btnClose to navigate back to GetStarted with left/right sliding animation
        btnClose.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, GetStarted.class);
            startActivity(intent);
            // Use left/right sliding animation for closing
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        });

        // Toggle password visibility
        btnTogglePwd.setOnClickListener(v -> {
            pwdVisible = !pwdVisible;
            toggleVisibility(etPassword, btnTogglePwd, pwdVisible);
        });

        // Email/Password Login
        btnLogin.setOnClickListener(v -> {

            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(LoginActivity.this, "Please enter email and password.", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(LoginActivity.this, task -> {
                        if (task.isSuccessful()) {
                            // Login successful; redirect to home page with bottom-up animation
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
                            finish();
                        } else {
                            String msg = task.getException() != null ? task.getException().getMessage() : "Authentication failed";
                            Toast.makeText(LoginActivity.this, "Authentication failed: " + msg, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Forgot password: show dialog to enter email and send reset link
        btnForgotPassword.setOnClickListener(v -> {
            final EditText input = new EditText(LoginActivity.this);
            input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            String prefill = etEmail.getText().toString().trim();
            if (!TextUtils.isEmpty(prefill)) {
                input.setText(prefill);
            }

            new AlertDialog.Builder(LoginActivity.this)
                    .setTitle("Reset password")
                    .setMessage("Enter your email address to receive a password reset link.")
                    .setView(input)
                    .setPositiveButton("Send", (dialog, which) -> {
                        String email = input.getText().toString().trim();

                        View rootView = findViewById(R.id.main); // for Snackbar

                        if (TextUtils.isEmpty(email)) {
                            Snackbar.make(rootView, "Please enter your email address.", Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            Snackbar.make(rootView, "Please enter a valid email address.", Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        // Show progress dialog while sending reset email
                        ProgressDialog progressDialog = new ProgressDialog(LoginActivity.this);
                        progressDialog.setMessage("Sending reset email...");
                        progressDialog.setCancelable(false);
                        progressDialog.show();

                        // Send password reset email
                        mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                            progressDialog.dismiss();
                            if (task.isSuccessful()) {
                                Snackbar.make(rootView, "Password reset email sent. Check your inbox.", Snackbar.LENGTH_LONG).show();
                            } else {
                                String err = task.getException() != null ? task.getException().getMessage() : "Failed to send reset email";
                                Snackbar.make(rootView, "Error: " + err, Snackbar.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Set listener on btnSignUp to move to SignUpActivity
        btnSignUp.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))  // web client ID from Firebase console
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleSignIn.setOnClickListener(v -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        // Optionally, you can add a click listener for btnForgotPassword to implement password reset.
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle Google Sign-In result
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                Toast.makeText(LoginActivity.this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
                        finish();
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Authentication with Google failed";
                        Toast.makeText(LoginActivity.this, "Authentication with Google failed: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Toggle between password hidden/visible */
    private void toggleVisibility(EditText et, ImageButton ib, boolean visible) {
        if (visible) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ib.setImageResource(R.drawable.ic_eye_on);
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ib.setImageResource(R.drawable.ic_eye_off);
        }
        et.setSelection(et.getText().length());
    }
}
