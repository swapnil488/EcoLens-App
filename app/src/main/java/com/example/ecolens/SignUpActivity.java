package com.example.ecolens;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SignUpActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etConfirmPassword;
    private TextView tvErrorMsg, tvVerifiedMsg;
    private Button btnVerifyEmail, btnSignUp;
    private ImageButton btnBack, btnTogglePwd, btnToggleConfirm;
    private View passwordContainer, confirmPasswordContainer;
    private boolean pwdVisible = false, confirmVisible = false;

    // Regex: ≥8 chars, 1 digit, 1 lower, 1 upper, 1 special
    private static final Pattern PWD_PATTERN = Pattern.compile(
            "^" +
                    "(?=.*[0-9])" +
                    "(?=.*[a-z])" +
                    "(?=.*[A-Z])" +
                    "(?=.*[@#$%^&+=!_–])" +
                    ".{8,}" +
                    "$"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        // edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return insets;
        });

        // bind views
        etEmail               = findViewById(R.id.etEmail2);
        etPassword            = findViewById(R.id.etPassword2);
        etConfirmPassword     = findViewById(R.id.etConfirmPassword);
        tvErrorMsg            = findViewById(R.id.tvErrorMsg2);
        tvVerifiedMsg         = findViewById(R.id.tvVerifiedMsg);
        btnVerifyEmail        = findViewById(R.id.btnVerifyEmail);
        btnSignUp             = findViewById(R.id.btnSignUp);
        btnBack               = findViewById(R.id.btnBack);
        btnTogglePwd          = findViewById(R.id.btnTogglePwd);
        btnToggleConfirm      = findViewById(R.id.btnToggleConfirm);
        passwordContainer     = findViewById(R.id.passwordContainer);
        confirmPasswordContainer = findViewById(R.id.confirmPasswordContainer);

        // initially hide password related views
        passwordContainer.setVisibility(View.GONE);
        confirmPasswordContainer.setVisibility(View.GONE);
        btnSignUp.setVisibility(View.GONE);
        tvVerifiedMsg.setVisibility(View.GONE);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Verify Email
        btnVerifyEmail.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                tvErrorMsg.setText("Please enter a valid email.");
                return;
            }
            tvErrorMsg.setText("");
            startActivity(new Intent(this, CheckEmailActivity.class)
                    .putExtra("email", email));
        });

        // Toggle password visibility
        btnTogglePwd.setOnClickListener(v -> {
            pwdVisible = !pwdVisible;
            toggleVisibility(etPassword, btnTogglePwd, pwdVisible);
        });
        btnToggleConfirm.setOnClickListener(v -> {
            confirmVisible = !confirmVisible;
            toggleVisibility(etConfirmPassword, btnToggleConfirm, confirmVisible);
        });

        // Final Sign Up
        btnSignUp.setOnClickListener(v -> {
            String pwd  = etPassword.getText().toString();
            String conf = etConfirmPassword.getText().toString();
            tvErrorMsg.setText("");

            // 1) check match
            if (!pwd.equals(conf)) {
                tvErrorMsg.setText("Passwords do not match.");
                return;
            }
            // 2) check strength
            if (!PWD_PATTERN.matcher(pwd).matches()) {
                tvErrorMsg.setText(
                        "Password must be ≥8 chars, include uppercase, lowercase, number & special char."
                );
                return;
            }

            // update dummy user's password
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                tvErrorMsg.setText("Session expired. Please start over.");
                return;
            }
            user.updatePassword(pwd)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Set a flag in Firestore to indicate registration is complete
                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("registrationComplete", true);

                            db.collection("users").document(user.getUid())
                                    .set(userData, SetOptions.merge())
                                    .addOnSuccessListener(aVoid -> {
                                        startActivity(new Intent(this, MainActivity.class));
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("SignUpActivity", "Failed to set registrationComplete flag", e);
                                        // Still proceed, as password was set.
                                        startActivity(new Intent(this, MainActivity.class));
                                        finish();
                                    });
                        } else {
                            tvErrorMsg.setText("Could not set password: "
                                    + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        }
                    });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent.getBooleanExtra("emailVerified", false)) {
            String email = intent.getStringExtra("verifiedEmail");
            etEmail.setText(email);
            etEmail.setEnabled(false);
            btnVerifyEmail.setVisibility(View.GONE);

            // show verified message & password fields
            tvVerifiedMsg.setVisibility(View.VISIBLE);
            passwordContainer.setVisibility(View.VISIBLE);
            confirmPasswordContainer.setVisibility(View.VISIBLE);
            btnSignUp.setVisibility(View.VISIBLE);

            intent.removeExtra("emailVerified");
        }
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
