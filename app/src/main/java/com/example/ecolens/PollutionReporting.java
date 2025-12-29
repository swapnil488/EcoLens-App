package com.example.ecolens;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class PollutionReporting extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_LOCATION_PICK = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 1002;

    private FrameLayout framePreview;
    private TextView tvPlaceholder;
    private ImageButton imagePreviewBtn;

    private TextView locationText;
    private LatLng selectedLocation;
    private String pincode = "";
    private GoogleMap googleMap;
    private Marker locationMarker;

    private String selectedCategory = "";
    private String selectedSeverity = "";
    private String titleText = "";
    private String descriptionText = "";
    private String status = "pending";
    private String photoUrl = "";

    private EditText etTitle, etDescription;
    private Button[] categoryButtons;
    private Button[] severityButtons;

    private String userName = "";
    private String uid = "";

    private StorageReference storageReference;

    private int uploadsInProgress = 0;

    // Multi-select flags for categories
    private boolean isAir = false;
    private boolean isSoil = false;
    private boolean isWater = false;
    private boolean isOther = false;

    // TFLite interpreter & classification result
    private Interpreter tflite = null;
    private String modelCategory = ""; // new field to upload to Firestore
    private static final float CONFIDENCE_THRESHOLD = 0.80f; // using 0.80 for model acceptance

    // Assumed model labels order — change if your model uses a different label order:
    private final String[] LABELS = new String[]{
            "Air", "Soil", "Water", "Air+Soil", "Air+Water", "Soil+Water", "Air+Soil+Water"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pollution_reporting);

        // Initialize tflite interpreter
        try {
            tflite = new Interpreter(loadModelFile("best_float32.tflite"));
            // Log model I/O info for diagnostics
            logModelInfo();
        } catch (IOException e) {
            Log.e("TFLite", "Failed to load tflite model", e);
            // If model loading fails, app still runs but classification won't happen.
            tflite = null;
        }

        storageReference = FirebaseStorage.getInstance().getReference().child("reports_images");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            uid = currentUser.getUid();
            userName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Unknown User";
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton btn_close = findViewById(R.id.btn_close);
        btn_close.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        framePreview    = findViewById(R.id.frame_image_preview);
        tvPlaceholder   = findViewById(R.id.tv_placeholder);
        imagePreviewBtn = findViewById(R.id.image_preview_btn);

        imagePreviewBtn.setOnClickListener(v -> {
            if (imagePreviewBtn.getDrawable() != null) {
                showFullScreen(imagePreviewBtn.getDrawable());
            }
        });

        Button btn_upload_image = findViewById(R.id.btn_upload_image);
        btn_upload_image.setOnClickListener(v -> openImagePicker());

        framePreview.setOnClickListener(v -> openImagePicker());

        locationText = findViewById(R.id.txt_location);
        Button btn_select_location = findViewById(R.id.btn_select_location);
        btn_select_location.setOnClickListener(v -> openMap());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);

        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                titleText = s.toString();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etDescription.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                descriptionText = s.toString();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Category buttons (multi-select among Air/Soil/Water; Other is exclusive)
        Button btnAir = findViewById(R.id.btn_air);
        Button btnWater = findViewById(R.id.btn_water);
        Button btnSoil = findViewById(R.id.btn_soil);
        Button btnOther = findViewById(R.id.btn_other);
        categoryButtons = new Button[]{btnAir, btnWater, btnSoil, btnOther};

        // set tags to identify
        btnAir.setTag("Air");
        btnSoil.setTag("Soil");
        btnWater.setTag("Water");
        btnOther.setTag("Other");

        for (Button btn : categoryButtons) {
            btn.setOnClickListener(v -> handleCategoryClick((Button) v));
        }

        Button btnMild = findViewById(R.id.btn_mild);
        Button btnModerate = findViewById(R.id.btn_moderate);
        Button btnSevere = findViewById(R.id.btn_severe);
        Button btnNotSure = findViewById(R.id.btn_not_sure);
        severityButtons = new Button[]{btnMild, btnModerate, btnSevere, btnNotSure};

        for (Button btn : severityButtons) {
            btn.setOnClickListener(v -> {
                clearSelection(severityButtons);
                updateSelectedBackground(btn);
                selectedSeverity = btn.getText().toString();
            });
        }

        Button btnSubmitReport = findViewById(R.id.btn_submit_report);
        btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    // ---------------------------
    // Diagnostic: log model I/O info
    // ---------------------------
    private void logModelInfo() {
        if (tflite == null) {
            Log.d("TFLiteInfo", "Interpreter is null — model not loaded.");
            return;
        }
        try {
            int[] inShape = tflite.getInputTensor(0).shape();
            org.tensorflow.lite.DataType inType = tflite.getInputTensor(0).dataType();
            int[] outShape = tflite.getOutputTensor(0).shape();
            org.tensorflow.lite.DataType outType = tflite.getOutputTensor(0).dataType();

            String info = "Input shape: ";
            for (int s : inShape) info += s + " ";
            info += " | Input dtype: " + inType.toString();
            info += " || Output shape: ";
            for (int s : outShape) info += s + " ";
            info += " | Output dtype: " + outType.toString();

            Log.d("TFLiteInfo", info);
        } catch (Exception e) {
            Log.e("TFLiteInfo", "Failed to read model I/O info", e);
        }
    }

    // ---------------------------
    // Category selection logic
    // ---------------------------
    private void handleCategoryClick(Button btn) {
        String tag = (String) btn.getTag();
        if (tag == null) return;

        switch (tag) {
            case "Other":
                // Toggle Other. If turning ON, clear Air/Soil/Water
                if (!isOther) {
                    isOther = true;
                    isAir = isSoil = isWater = false;
                } else {
                    isOther = false;
                }
                break;
            case "Air":
                if (isOther) isOther = false;
                isAir = !isAir;
                break;
            case "Soil":
                if (isOther) isOther = false;
                isSoil = !isSoil;
                break;
            case "Water":
                if (isOther) isOther = false;
                isWater = !isWater;
                break;
        }

        updateCategoryButtonsUI();
        buildSelectedCategoryString();
    }

    private void updateCategoryButtonsUI() {
        for (Button btn : categoryButtons) {
            String tag = (String) btn.getTag();
            if (tag == null) continue;
            boolean selected = false;
            switch (tag) {
                case "Air": selected = isAir; break;
                case "Soil": selected = isSoil; break;
                case "Water": selected = isWater; break;
                case "Other": selected = isOther; break;
            }
            setButtonSelected(btn, selected);
        }
    }

    private void setButtonSelected(Button button, boolean selected) {
        if (selected) {
            button.setBackgroundColor(getResources().getColor(R.color.selectedButtonColor));
        } else {
            button.setBackgroundColor(getResources().getColor(R.color.defaultButtonColor));
        }
    }

    private void clearSelection(Button[] buttons) {
        for (Button btn : buttons) {
            btn.setBackgroundColor(getResources().getColor(R.color.defaultButtonColor));
        }
    }

    private void updateSelectedBackground(Button button) {
        button.setBackgroundColor(getResources().getColor(R.color.selectedButtonColor));
    }

    private void buildSelectedCategoryString() {
        if (isOther) {
            selectedCategory = "Other";
            return;
        }

        StringBuilder sb = new StringBuilder();
        // Consistent order: Air, Soil, Water
        if (isAir) sb.append("Air");
        if (isSoil) {
            if (sb.length() > 0) sb.append("+");
            sb.append("Soil");
        }
        if (isWater) {
            if (sb.length() > 0) sb.append("+");
            sb.append("Water");
        }
        selectedCategory = sb.toString(); // may be empty if nothing selected
    }

    // ---------------------------
    // Image picking & uploading
    // ---------------------------
    private void openImagePicker() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            launchImagePicker();
        }
    }

    private void launchImagePicker() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        Intent chooserIntent = Intent.createChooser(pickIntent, "Select or Take a New Picture");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takePictureIntent});

        startActivityForResult(chooserIntent, REQUEST_IMAGE_PICK);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchImagePicker();
            }
        }
    }

    private void openMap() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        startActivityForResult(intent, REQUEST_LOCATION_PICK);
    }

    @Override
    public void onMapReady(GoogleMap gMap) {
        googleMap = gMap;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_IMAGE_PICK) {
                photoUrl = "";
                if (data.getData() != null) {
                    Uri selectedImageUri = data.getData();
                    displayPreview(selectedImageUri);
                    // upload and then classify
                    uploadImageFromUri(selectedImageUri);
                } else if (data.getExtras() != null) {
                    Bitmap capturedBitmap = (Bitmap) data.getExtras().get("data");
                    if (capturedBitmap != null) {
                        displayPreview(capturedBitmap);
                        uploadImageFromBitmap(capturedBitmap);
                    }
                }
            } else if (requestCode == REQUEST_LOCATION_PICK) {
                double lat = data.getDoubleExtra("latitude", 0);
                double lng = data.getDoubleExtra("longitude", 0);
                pincode = data.getStringExtra("pincode");
                selectedLocation = new LatLng(lat, lng);
                locationText.setText("Selected Location: " + lat + ", " + lng);
                updateMapLocation(selectedLocation);
            }
        }
    }

    private void displayPreview(Uri uri) {
        tvPlaceholder.setVisibility(View.GONE);
        imagePreviewBtn.setVisibility(View.VISIBLE);
        imagePreviewBtn.setImageURI(uri);
    }

    private void displayPreview(Bitmap bmp) {
        tvPlaceholder.setVisibility(View.GONE);
        imagePreviewBtn.setVisibility(View.VISIBLE);
        imagePreviewBtn.setImageBitmap(bmp);
    }

    private void showFullScreen(android.graphics.drawable.Drawable drawable) {
        Dialog dlg = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(R.layout.dialog_fullscreen_image);
        ImageView full = dlg.findViewById(R.id.full_image);
        full.setImageDrawable(drawable);
        full.setOnClickListener(v -> dlg.dismiss());
        dlg.show();
    }

    // ---------------------------
    // Upload image (Uri)
    // ---------------------------
    private void uploadImageFromUri(Uri imageUri) {
        uploadsInProgress++;
        StorageReference imageRef = storageReference.child(System.currentTimeMillis() + ".jpg");
        UploadTask uploadTask = imageRef.putFile(imageUri);
        uploadTask.addOnSuccessListener(taskSnapshot ->
                imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    photoUrl = uri.toString();
                    Toast.makeText(PollutionReporting.this, "Image uploaded", Toast.LENGTH_SHORT).show();
                    uploadsInProgress--;

                    // Attempt to load bitmap from Uri and run model classification (if model loaded)
                    if (tflite != null) {
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                            if (bitmap != null) runClassification(bitmap);
                        } catch (IOException e) {
                            Log.e("Classification", "Failed to read bitmap for classification", e);
                        }
                    } else {
                        // If no model, mark modelCategory empty
                        modelCategory = "";
                    }
                })
        ).addOnFailureListener(e -> {
            Toast.makeText(PollutionReporting.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
            Log.e("UploadError", e.getMessage());
            uploadsInProgress--;
        });
    }

    // ---------------------------
    // Upload image (Bitmap)
    // ---------------------------
    private void uploadImageFromBitmap(Bitmap bitmap) {
        uploadsInProgress++;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        StorageReference imageRef = storageReference.child(System.currentTimeMillis() + ".jpg");
        UploadTask uploadTask = imageRef.putBytes(data);
        uploadTask.addOnSuccessListener(taskSnapshot ->
                imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    photoUrl = uri.toString();
                    Toast.makeText(PollutionReporting.this, "Image uploaded", Toast.LENGTH_SHORT).show();
                    uploadsInProgress--;

                    // Run classification (bitmap is already available)
                    if (tflite != null) {
                        runClassification(bitmap);
                    } else {
                        modelCategory = "";
                    }
                })
        ).addOnFailureListener(e -> {
            Toast.makeText(PollutionReporting.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
            Log.e("UploadError", e.getMessage());
            uploadsInProgress--;
        });
    }

    // ---------------------------
    // Classification helpers
    // ---------------------------
    private void runClassification(Bitmap bitmap) {
        if (tflite == null) return;

        new Thread(() -> {
            try {
                // Determine input size from model (expects [1, height, width, 3])
                int[] inputShape = tflite.getInputTensor(0).shape(); // {1, h, w, 3}
                int inputH = inputShape[1];
                int inputW = inputShape[2];

                // Resize bitmap
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true);

                // Prepare input: float[1][h][w][3], normalized [0,1] if model expects float
                float[][][][] input = new float[1][inputH][inputW][3];

                org.tensorflow.lite.DataType inputType = tflite.getInputTensor(0).dataType();
                boolean isFloatModel = (inputType == org.tensorflow.lite.DataType.FLOAT32);

                for (int y = 0; y < inputH; y++) {
                    for (int x = 0; x < inputW; x++) {
                        int px = resized.getPixel(x, y);
                        float r = (float) ((px >> 16) & 0xFF);
                        float g = (float) ((px >> 8) & 0xFF);
                        float b = (float) (px & 0xFF);

                        if (isFloatModel) {
                            // Normalize to [0,1] exactly like the Python preprocessing
                            r /= 255.0f;
                            g /= 255.0f;
                            b /= 255.0f;
                        }
                        input[0][y][x][0] = r;
                        input[0][y][x][1] = g;
                        input[0][y][x][2] = b;
                    }
                }

                // Output: float[1][7] (assumes float output)
                float[][] output = new float[1][LABELS.length];
                tflite.run(input, output);

                float[] scores = output[0];
                int maxIdx = 0;
                float maxScore = scores[0];
                for (int i = 1; i < scores.length; i++) {
                    if (scores[i] > maxScore) {
                        maxScore = scores[i];
                        maxIdx = i;
                    }
                }

                final float confidence = maxScore;
                final String predictedLabel = (confidence >= CONFIDENCE_THRESHOLD) ? LABELS[maxIdx] : "Other";

                // Log detailed output for debugging
                StringBuilder logScores = new StringBuilder();
                for (int i = 0; i < scores.length; i++) {
                    logScores.append(LABELS[i]).append(": ").append(String.format("%.3f", scores[i])).append("  ");
                }
                Log.d("TFLiteOutput", "Scores: " + logScores.toString());

                runOnUiThread(() -> {
                    modelCategory = predictedLabel;
                    // NOTE: Intentionally NOT applying model category to user's selection or UI.
                    String msg = "Model: " + predictedLabel + " (conf: " + String.format("%.2f", confidence) + ")";
                    Toast.makeText(PollutionReporting.this, msg, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e("TFLiteClassify", "classification failed", e);
            }
        }).start();
    }

    // ---------------------------
    // Map helpers
    // ---------------------------
    private void updateMapLocation(LatLng location) {
        if (googleMap != null) {
            if (locationMarker != null) {
                locationMarker.remove();
            }
            locationMarker = googleMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Pollution Hotspot"));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        }
    }

    // ---------------------------
    // Submit report to Firestore
    // ---------------------------
    private void submitReport() {
        if (uploadsInProgress > 0) {
            Toast.makeText(PollutionReporting.this, "Please wait, image is being uploaded", Toast.LENGTH_SHORT).show();
            return;
        }

        if (titleText.isEmpty() || descriptionText.isEmpty() || selectedCategory.isEmpty() ||
                selectedSeverity.isEmpty() || selectedLocation == null || photoUrl.isEmpty()) {
            Toast.makeText(PollutionReporting.this, "Please fill in all required details and add a photo", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("title", titleText);
        reportData.put("description", descriptionText);
        reportData.put("category", selectedCategory);     // user-selected category (unchanged by model)
        reportData.put("modelCategory", modelCategory);   // NEW field from model (may be "Other" or "")
        reportData.put("severity", selectedSeverity);
        reportData.put("latitude", selectedLocation.latitude);
        reportData.put("longitude", selectedLocation.longitude);
        reportData.put("pincode", pincode);
        reportData.put("photoUrl", photoUrl);
        reportData.put("userName", userName);
        reportData.put("uid", uid);
        reportData.put("status", status);
        reportData.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("reports")
                .add(reportData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(PollutionReporting.this, "Report submitted successfully", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(PollutionReporting.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(PollutionReporting.this, "Failed to submit report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // No reward listeners to remove anymore
    }

    // ---------------------------
    // TFLite model loader
    // ---------------------------
    private MappedByteBuffer loadModelFile(String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
