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
    private TextView profileEmail; // added field for email
    private Uri profileImageUri; // holds the selected image URI

    // New views for logout/delete as cards + icons
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

        // Apply system bar insets for padding (edge-to-edge support)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, windowInsets) -> {
            androidx.core.graphics.Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return windowInsets;
        });

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Find views
        btnClose = findViewById(R.id.btn_close);
        profileImage = findViewById(R.id.profile_image);
        profileName = findViewById(R.id.profile_name);
        profileEmail = findViewById(R.id.profile_email); // initialize email TextView

        // Material cards for View Reports / My Rewards
        btnViewReportsCard = findViewById(R.id.viewReports);

        // Material cards for Sharing App
        shareCard = findViewById(R.id.share_card);


        // Logout / delete rows and icons
        logoutCard = findViewById(R.id.logout_card);
        deleteCard = findViewById(R.id.delete_card);
        logoutIcon = findViewById(R.id.logout_icon);
        deleteIcon = findViewById(R.id.delete_icon);

        // Wire ViewReports navigation
        btnViewReportsCard.setOnClickListener(v -> {
            Intent intent = new Intent(Profile.this, Reports.class);
            startActivity(intent);
        });

        // Share Button Functioning
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


        // Set close button behavior
        btnClose.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        // Populate UI from current FirebaseUser
        populateProfileFromUser();

        // Allow user to update profile image (open options to take photo or pick from gallery)
        profileImage.setOnClickListener(v -> showImagePickerDialog());

        // Allow user to edit their name
        profileName.setOnClickListener(v -> showEditNameDialog());

        // Make both the icon and the row do the same action for logout and delete
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
            // Display name
            String displayName = user.getDisplayName();
            profileName.setText(displayName != null && !displayName.isEmpty() ? displayName : "Name");

            // Display email (explicitly from Firebase user)
            String email = user.getEmail();
            if (profileEmail != null) {
                profileEmail.setText(email != null ? email : "");
            }

            // Display profile image using Glide (works with URLs and content URIs)
            if (user.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(user.getPhotoUrl())
                        .centerCrop()
                        .into(profileImage);
            } // else leave placeholder from XML
        } else {
            // no user: clear UI
            profileName.setText("Name");
            if (profileEmail != null) profileEmail.setText("");
            // placeholder image remains
        }
    }

    // Show a dialog letting the user choose between taking a photo or picking from the gallery
    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Option")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (dialog, which) -> {
                    if (which == 0) {
                        // Open the camera (thumbnail)
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                        } else {
                            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Open the gallery
                        Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        if (pickPhoto.resolveActivity(getPackageManager()) != null) {
                            startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK);
                        } else {
                            Toast.makeText(this, "Gallery not available", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        builder.show();
    }

    // Show a dialog to edit the user's name
    private void showEditNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Name");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(profileName.getText());
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                profileName.setText(newName); // update UI immediately
                updateUserProfile(newName, profileImageUri);
            } else {
                Toast.makeText(Profile.this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    // Handle results from the camera or gallery intents
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                if (data != null && data.getExtras() != null) {
                    Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                    if (imageBitmap != null) {
                        // Convert to URI so we can upload
                        Uri uri = getImageUriFromBitmap(imageBitmap);
                        if (uri != null) {
                            profileImageUri = uri;
                            // Use Glide to show the selected image immediately
                            Glide.with(this).load(profileImageUri).centerCrop().into(profileImage);
                            // update profile with new photo and current name
                            updateUserProfile(profileName.getText().toString(), profileImageUri);
                        } else {
                            Toast.makeText(this, "Failed to obtain image URI", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "No image returned from camera", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "No image returned from camera", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_IMAGE_PICK) {
                if (data != null) {
                    Uri selectedImage = data.getData();
                    if (selectedImage != null) {
                        profileImageUri = selectedImage;
                        // Use Glide to show the selected image immediately
                        Glide.with(this).load(profileImageUri).centerCrop().into(profileImage);
                        updateUserProfile(profileName.getText().toString(), profileImageUri);
                    } else {
                        Toast.makeText(this, "Failed to pick image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    // Helper method to convert a Bitmap to a Uri (for uploading)
    private Uri getImageUriFromBitmap(Bitmap bitmap) {
        // Insert image into MediaStore and get a content Uri
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "ProfileImage", null);
        return path == null ? null : Uri.parse(path);
    }

    // Logout flow - now navigates to GetStarted and clears backstack
    private void performLogout() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        navigateToGetStarted();
    }

    // Delete account confirmation
    private void confirmAndDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Delete account")
                .setMessage("Are you sure you want to permanently delete your account? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .show();
    }

    // Delete account: tries to delete FirebaseAuth user and Firestore document
    private void deleteAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No signed-in user", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save UID now (safe to reference after async delete)
        String uid = user.getUid();

        // Attempt to delete. Note: Firebase may require recent authentication.
        user.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Remove user data from Firestore as well (best-effort)
                db.collection("users").document(uid).delete();

                // Optionally remove profile image from storage
                StorageReference picRef = storage.getReference().child("profile_images").child(uid);
                picRef.delete().addOnCompleteListener(delTask -> {
                    // ignore result — best-effort cleanup
                });

                Toast.makeText(Profile.this, "Account deleted successfully", Toast.LENGTH_SHORT).show();

                // Also sign out to be safe and navigate to GetStarted
                mAuth.signOut();
                navigateToGetStarted();

            } else {
                Exception e = task.getException();
                if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                    // Needs re-auth - inform user and send to GetStarted so they can sign-in again
                    showReauthRequiredDialog();
                } else {
                    String msg = e != null ? e.getMessage() : "Account deletion failed";
                    Toast.makeText(Profile.this, msg, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // Show a friendly dialog telling the user to re-login for sensitive operations
    private void showReauthRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Re-login required")
                .setMessage("For security reasons, please sign out and sign in again (recent authentication required) before deleting your account.")
                .setPositiveButton("Sign out & go to Get Started", (dialog, which) -> {
                    performLogout(); // performLogout will sign out and navigate to GetStarted
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Update the Firebase user profile and save the new details to Firestore
    private void updateUserProfile(String name, Uri photoUri) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No signed-in user", Toast.LENGTH_SHORT).show();
            return;
        }

        // If there is a new photo, upload it first
        if (photoUri != null) {
            StorageReference storageRef = storage.getReference().child("profile_images").child(user.getUid());
            storageRef.putFile(photoUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        // Image upload succeeded
                        storageRef.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                            // Update Firebase Auth profile with new name and photo URL
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .setPhotoUri(downloadUrl)
                                    .build();
                            user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    // Update UI immediately
                                    profileName.setText(name);
                                    Glide.with(Profile.this).load(downloadUrl).centerCrop().into(profileImage);

                                    // Ensure email field stays correct (refresh from user)
                                    populateProfileFromUser();

                                    saveUserToFirestore(user.getUid(), name, downloadUrl.toString());
                                    Toast.makeText(Profile.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                                } else {
                                    String err = task.getException() != null ? task.getException().getMessage() : "Profile update failed";
                                    Toast.makeText(Profile.this, err, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).addOnFailureListener(e -> {
                            Toast.makeText(Profile.this, "Failed to get image URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(Profile.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            // If only updating the name without a new photo, keep existing photo if present
            UserProfileChangeRequest.Builder builder = new UserProfileChangeRequest.Builder().setDisplayName(name);
            if (user.getPhotoUrl() != null) {
                builder.setPhotoUri(user.getPhotoUrl());
            }
            UserProfileChangeRequest profileUpdates = builder.build();
            user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Update UI immediately
                    profileName.setText(name);
                    if (user.getPhotoUrl() != null) {
                        Glide.with(Profile.this).load(user.getPhotoUrl()).centerCrop().into(profileImage);
                    }
                    // Refresh email/name from FirebaseUser to be safe
                    populateProfileFromUser();

                    String existingPhoto = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
                    saveUserToFirestore(user.getUid(), name, existingPhoto);
                    Toast.makeText(Profile.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                } else {
                    String err = task.getException() != null ? task.getException().getMessage() : "Profile update failed";
                    Toast.makeText(Profile.this, err, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Save or update the user information in Firestore under a collection called "users"
    private void saveUserToFirestore(String uid, String name, String photoUrl) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("name", name);
        if (photoUrl != null) {
            userData.put("photoUrl", photoUrl);
        }
        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    // saved
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Profile.this, "Failed to save user info: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Helper to navigate to GetStarted and clear backstack so user can't return to previous screens
    private void navigateToGetStarted() {
        Intent intent = new Intent(Profile.this, GetStarted.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
