package com.example.ecolens;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class Profile extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_IMAGE_PICK = 101;

    private ImageButton btnClose;
    private MaterialCardView btnViewReportsCard;
    private MaterialCardView shareCard;
    private ImageButton profileImage;
    private TextView profileName;
    private TextView profileEmail;

    private Uri profileImageUri;

    private LinearLayout logoutCard;
    private LinearLayout deleteCard;
    private ImageButton logoutIcon;
    private ImageButton deleteIcon;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, windowInsets) -> {
            androidx.core.graphics.Insets systemBars =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top,
                    systemBars.right, systemBars.bottom);
            return windowInsets;
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        btnClose = findViewById(R.id.btn_close);
        profileImage = findViewById(R.id.profile_image);
        profileName = findViewById(R.id.profile_name);
        profileEmail = findViewById(R.id.profile_email);

        btnViewReportsCard = findViewById(R.id.viewReports);
        shareCard = findViewById(R.id.share_card);

        logoutCard = findViewById(R.id.logout_card);
        deleteCard = findViewById(R.id.delete_card);
        logoutIcon = findViewById(R.id.logout_icon);
        deleteIcon = findViewById(R.id.delete_icon);

        btnViewReportsCard.setOnClickListener(v ->
                startActivity(new Intent(Profile.this, Reports.class)));

        shareCard.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String name = (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                    ? user.getDisplayName()
                    : "A user";

            String shareMessage =
                    name + " has shared EcoLens with you.\n\n" +
                            "Check it out here:\n" +
                            "https://github.com/swapnil488/EcoLens-App";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);

            startActivity(Intent.createChooser(shareIntent, "Share EcoLens via"));
        });

        btnClose.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        populateProfileFromUser();

        profileImage.setOnClickListener(v -> showImagePickerDialog());
        profileName.setOnClickListener(v -> showEditNameDialog());

        View.OnClickListener doLogout = v -> performLogout();
        View.OnClickListener doDelete = v -> confirmAndDeleteAccount();

        logoutIcon.setOnClickListener(doLogout);
        logoutCard.setOnClickListener(doLogout);
        deleteIcon.setOnClickListener(doDelete);
        deleteCard.setOnClickListener(doDelete);
    }

    private void populateProfileFromUser() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            profileName.setText(
                    user.getDisplayName() != null ? user.getDisplayName() : "Name");
            profileEmail.setText(user.getEmail() != null ? user.getEmail() : "");
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).centerCrop().into(profileImage);
            }
        }
    }

    private void showImagePickerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Select Option")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (d, w) -> {
                    if (w == 0) {
                        startActivityForResult(
                                new Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                                REQUEST_IMAGE_CAPTURE);
                    } else {
                        startActivityForResult(
                                new Intent(Intent.ACTION_PICK,
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                                REQUEST_IMAGE_PICK);
                    }
                }).show();
    }

    private void showEditNameDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(profileName.getText());

        new AlertDialog.Builder(this)
                .setTitle("Edit Name")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateUserProfile(newName, profileImageUri);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK) {
            if (req == REQUEST_IMAGE_CAPTURE && data != null) {
                Bitmap bmp = (Bitmap) data.getExtras().get("data");
                profileImageUri = getImageUriFromBitmap(bmp);
            } else if (req == REQUEST_IMAGE_PICK && data != null) {
                profileImageUri = data.getData();
            }
            if (profileImageUri != null) {
                Glide.with(this).load(profileImageUri).centerCrop().into(profileImage);
                updateUserProfile(profileName.getText().toString(), profileImageUri);
            }
        }
    }

    private Uri getImageUriFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(
                getContentResolver(), bitmap, "ProfileImage", null);
        return Uri.parse(path);
    }

    private void performLogout() {
        mAuth.signOut();
        navigateToGetStarted();
    }

    private void confirmAndDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Delete account")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (d, w) -> deleteAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        user.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                db.collection("users").document(uid).delete();
                storage.getReference().child("profile_images").child(uid).delete();
                navigateToGetStarted();
            } else if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                performLogout();
            }
        });
    }

    private void updateUserProfile(String name, Uri photoUri) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        if (photoUri != null) {
            StorageReference ref = storage.getReference()
                    .child("profile_images").child(user.getUid());
            ref.putFile(photoUri)
                    .addOnSuccessListener(t ->
                            ref.getDownloadUrl().addOnSuccessListener(url -> {
                                UserProfileChangeRequest req =
                                        new UserProfileChangeRequest.Builder()
                                                .setDisplayName(name)
                                                .setPhotoUri(url)
                                                .build();
                                user.updateProfile(req);
                                saveUserToFirestore(user.getUid(), name, url.toString());
                            }));
        }
    }

    private void saveUserToFirestore(String uid, String name, String photoUrl) {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("name", name);
        map.put("photoUrl", photoUrl);
        db.collection("users").document(uid).set(map);
    }

    private void navigateToGetStarted() {
        Intent i = new Intent(Profile.this, GetStarted.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }
}
