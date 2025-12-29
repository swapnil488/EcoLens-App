package com.example.ecolens;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportDetails extends AppCompatActivity implements OnMapReadyCallback {

    private TextView tvReportId, tvTitle, tvDescription, tvCategory, tvModelCategory, tvSeverity, tvStatus, tvNewDescription;
    private LinearLayout llImages, llNewImages;
    private ImageButton btnClose;
    private FrameLayout mapContainer;
    private SupportMapFragment mapFragment;
    private GoogleMap googleMap;
    private Double latitude = null, longitude = null;   // null means unknown
    private String reportId;
    private FirebaseFirestore db;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    // Main single image preview controls
    private FrameLayout frameImagePreview;
    private TextView tvPlaceholder;
    private ImageButton imagePreviewBtn;

    // Resolved fields (from XML)
    private TextView tvResolutionLabel;
    private TextView tvResolutionDescriptionLabel;
    private TextView tvResolutionDescription;
    private TextView tvResolvedAtLabel;
    private TextView tvResolvedAt;
    private TextView tvResolvedPhotoLabel;
    private FrameLayout cardResolvedPhoto;
    private TextView tvResolvedPhotoPlaceholder;
    private ImageButton imageResolvedPreviewBtn;

    // Container (not used to create resolution views anymore)
    private ViewGroup contentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_details);

        // Apply window insets for proper padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();

        btnClose = findViewById(R.id.btn_close);
        tvReportId = findViewById(R.id.tv_report_id);
        tvTitle = findViewById(R.id.tv_title);
        tvDescription = findViewById(R.id.tv_description);
        tvCategory = findViewById(R.id.tv_category);
        tvModelCategory = findViewById(R.id.tv_model_category);
        tvSeverity = findViewById(R.id.tv_severity);
        tvStatus = findViewById(R.id.tv_status);
        tvNewDescription = findViewById(R.id.tv_new_description);
        llImages = findViewById(R.id.ll_images);           // legacy - horizontal list fallback
        llNewImages = findViewById(R.id.ll_new_images);    // legacy
        mapContainer = findViewById(R.id.map_container);

        // Main image preview
        frameImagePreview = findViewById(R.id.frame_image_preview);
        tvPlaceholder = findViewById(R.id.tv_placeholder);
        imagePreviewBtn = findViewById(R.id.image_preview_btn);

        // Resolved fields (initialize)
        tvResolutionLabel = findViewById(R.id.tv_resolution_label);
        tvResolutionDescriptionLabel = findViewById(R.id.tv_resolution_description_label);
        tvResolutionDescription = findViewById(R.id.tv_resolution_description);
        tvResolvedAtLabel = findViewById(R.id.tv_resolved_at_label);
        tvResolvedAt = findViewById(R.id.tv_resolved_at);
        tvResolvedPhotoLabel = findViewById(R.id.tv_resolved_photo_label);
        cardResolvedPhoto = findViewById(R.id.card_resolved_photo);
        tvResolvedPhotoPlaceholder = findViewById(R.id.tv_resolved_photo_placeholder);
        imageResolvedPreviewBtn = findViewById(R.id.image_resolved_preview_btn);

        // Determine content container (the LinearLayout inside the ScrollView)
        ScrollView sv = findViewById(R.id.main);
        if (sv != null && sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup) {
            contentContainer = (ViewGroup) sv.getChildAt(0);
        } else {
            contentContainer = findViewById(android.R.id.content);
        }

        // Set close button behavior
        btnClose.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        reportId = getIntent().getStringExtra("reportId");
        if (reportId == null) {
            Toast.makeText(this, "No report ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadReportDetails();

        // Set up map fragment programmatically
        FragmentManager fm = getSupportFragmentManager();
        mapFragment = (SupportMapFragment) fm.findFragmentById(R.id.map_container);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            fm.beginTransaction().replace(R.id.map_container, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        // Image preview full-screen handlers
        if (imagePreviewBtn != null) {
            imagePreviewBtn.setOnClickListener(v -> {
                Object tag = imagePreviewBtn.getTag();
                if (tag instanceof String) {
                    openFullScreenImage((String) tag);
                }
            });
        }
        if (imageResolvedPreviewBtn != null) {
            imageResolvedPreviewBtn.setOnClickListener(v -> {
                Object tag = imageResolvedPreviewBtn.getTag();
                if (tag instanceof String) {
                    openFullScreenImage((String) tag);
                }
            });
        }
    }

    private void loadReportDetails() {
        db.collection("reports").document(reportId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        populateReportDetails(documentSnapshot);
                    } else {
                        Toast.makeText(ReportDetails.this, "Report not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ReportDetails.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void populateReportDetails(DocumentSnapshot doc) {
        // Basic fields
        tvReportId.setText(doc.getId());
        tvTitle.setText(safeGetString(doc, "title"));
        tvDescription.setText(safeGetString(doc, "description"));
        tvCategory.setText(safeGetString(doc, "category"));
        tvModelCategory.setText(safeGetString(doc, "modelCategory"));
        tvSeverity.setText(safeGetString(doc, "severity"));

        // Location
        if (doc.contains("latitude") && doc.contains("longitude")) {
            Double lat = getDoubleSafe(doc, "latitude");
            Double lng = getDoubleSafe(doc, "longitude");
            if (lat != null && lng != null) {
                latitude = lat;
                longitude = lng;
            }
        }

        // Status and color
        String status = safeGetString(doc, "status");
        tvStatus.setText(status != null ? status : "");
        if ("pending".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else if ("resolved".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }

        // IMAGE: single image handling (prefer "photoUrl", else first of "photoUrls")
        String photoUrl = null;
        if (doc.contains("photoUrl")) {
            photoUrl = safeGetString(doc, "photoUrl");
        } else if (doc.contains("photoUrls")) {
            Object arr = doc.get("photoUrls");
            if (arr instanceof List) {
                List<?> list = (List<?>) arr;
                if (!list.isEmpty() && list.get(0) instanceof String) {
                    photoUrl = (String) list.get(0);
                }
            }
        }

        if (photoUrl != null && !photoUrl.isEmpty()) {
            final String finalPhotoUrl = photoUrl;
            if (imagePreviewBtn != null && frameImagePreview != null && tvPlaceholder != null) {
                tvPlaceholder.setVisibility(View.GONE);
                imagePreviewBtn.setVisibility(View.VISIBLE);
                imagePreviewBtn.setAdjustViewBounds(true);
                Glide.with(this).load(finalPhotoUrl).centerCrop().into(imagePreviewBtn);
                imagePreviewBtn.setTag(finalPhotoUrl);
            } else if (llImages != null) {
                llImages.removeAllViews();
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.width = getResources().getDisplayMetrics().widthPixels - (int)(16 * getResources().getDisplayMetrics().density);
                params.setMargins(8, 8, 8, 8);
                iv.setLayoutParams(params);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setAdjustViewBounds(true);
                Glide.with(this).load(finalPhotoUrl).into(iv);
                iv.setOnClickListener(v -> openFullScreenImage(finalPhotoUrl));
                llImages.addView(iv);
            }
        } else {
            if (tvPlaceholder != null) tvPlaceholder.setVisibility(View.VISIBLE);
        }

        // New description (if any)
        String newDesc = safeGetString(doc, "newDescription");
        if (newDesc != null && !newDesc.isEmpty()) {
            tvNewDescription.setText(newDesc);
            tvNewDescription.setVisibility(View.VISIBLE);
            View label = findViewById(R.id.tv_new_description_label);
            if (label != null) label.setVisibility(View.VISIBLE);
        }

        // Updated images (legacy) - show first updated if present
        List<String> newImages = (List<String>) doc.get("newPhotoUrls");
        if (newImages != null && !newImages.isEmpty()) {
            final String newImageUrl = newImages.get(0);
            if (imagePreviewBtn != null && newImageUrl != null && !newImageUrl.isEmpty()) {
                tvPlaceholder.setVisibility(View.GONE);
                imagePreviewBtn.setVisibility(View.VISIBLE);
                Glide.with(this).load(newImageUrl).centerCrop().into(imagePreviewBtn);
                imagePreviewBtn.setTag(newImageUrl);
                View label = findViewById(R.id.tv_new_images_label);
                if (label != null) label.setVisibility(View.VISIBLE);
                View hs = findViewById(R.id.hs_new_images);
                if (hs != null) hs.setVisibility(View.VISIBLE);
            }
        }

        // RESOLUTION: show only when resolved
        if ("resolved".equalsIgnoreCase(status)) {
            showResolutionFields(doc);
        } else {
            hideResolutionFields();
        }

        // update map if ready
        updateMapIfReady();
    }

    private void showResolutionFields(DocumentSnapshot doc) {
        // Make labels visible
        if (tvResolutionLabel != null) tvResolutionLabel.setVisibility(View.VISIBLE);

        String resDesc = safeGetString(doc, "resolutionDescription");
        if (tvResolutionDescriptionLabel != null) tvResolutionDescriptionLabel.setVisibility(resDesc != null && !resDesc.isEmpty() ? View.VISIBLE : View.GONE);
        if (tvResolutionDescription != null) {
            if (resDesc != null && !resDesc.isEmpty()) {
                tvResolutionDescription.setText(resDesc);
                tvResolutionDescription.setVisibility(View.VISIBLE);
            } else {
                tvResolutionDescription.setVisibility(View.GONE);
            }
        }

        // resolvedAt
        String resolvedAtStr = null;
        Object resolvedAtObj = doc.get("resolvedAt");
        if (resolvedAtObj instanceof Timestamp) {
            Date d = ((Timestamp) resolvedAtObj).toDate();
            resolvedAtStr = dateFormat.format(d);
        } else if (resolvedAtObj instanceof Date) {
            resolvedAtStr = dateFormat.format((Date) resolvedAtObj);
        } else if (resolvedAtObj != null) {
            resolvedAtStr = resolvedAtObj.toString();
        }

        if (tvResolvedAtLabel != null) tvResolvedAtLabel.setVisibility(resolvedAtStr != null ? View.VISIBLE : View.GONE);
        if (tvResolvedAt != null) {
            if (resolvedAtStr != null) {
                tvResolvedAt.setText(resolvedAtStr);
                tvResolvedAt.setVisibility(View.VISIBLE);
            } else {
                tvResolvedAt.setVisibility(View.GONE);
            }
        }

        // resolved photo
        String resolvedPhotoUrl = safeGetString(doc, "resolvedPhotoUrl");
        if (resolvedPhotoUrl != null && !resolvedPhotoUrl.isEmpty()) {
            if (tvResolvedPhotoLabel != null) tvResolvedPhotoLabel.setVisibility(View.VISIBLE);
            if (cardResolvedPhoto != null) cardResolvedPhoto.setVisibility(View.VISIBLE);
            if (tvResolvedPhotoPlaceholder != null) tvResolvedPhotoPlaceholder.setVisibility(View.GONE);
            if (imageResolvedPreviewBtn != null) {
                imageResolvedPreviewBtn.setVisibility(View.VISIBLE);
                final String finalResolvedPhoto = resolvedPhotoUrl;
                Glide.with(this).load(finalResolvedPhoto).centerCrop().into(imageResolvedPreviewBtn);
                imageResolvedPreviewBtn.setTag(finalResolvedPhoto);
            }
        } else {
            if (tvResolvedPhotoLabel != null) tvResolvedPhotoLabel.setVisibility(View.GONE);
            if (cardResolvedPhoto != null) cardResolvedPhoto.setVisibility(View.GONE);
        }
    }

    private void hideResolutionFields() {
        if (tvResolutionLabel != null) tvResolutionLabel.setVisibility(View.GONE);
        if (tvResolutionDescriptionLabel != null) tvResolutionDescriptionLabel.setVisibility(View.GONE);
        if (tvResolutionDescription != null) tvResolutionDescription.setVisibility(View.GONE);
        if (tvResolvedAtLabel != null) tvResolvedAtLabel.setVisibility(View.GONE);
        if (tvResolvedAt != null) tvResolvedAt.setVisibility(View.GONE);
        if (tvResolvedPhotoLabel != null) tvResolvedPhotoLabel.setVisibility(View.GONE);
        if (cardResolvedPhoto != null) cardResolvedPhoto.setVisibility(View.GONE);
        if (imageResolvedPreviewBtn != null) imageResolvedPreviewBtn.setVisibility(View.GONE);
        if (tvResolvedPhotoPlaceholder != null) tvResolvedPhotoPlaceholder.setVisibility(View.VISIBLE);
    }

    private void updateMapIfReady() {
        if (googleMap != null && latitude != null && longitude != null) {
            LatLng location = new LatLng(latitude, longitude);

            googleMap.clear();
            googleMap.addMarker(new MarkerOptions().position(location).title("Report Location"));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));

            googleMap.setOnMapClickListener(latLng -> openInGoogleMaps(latLng.latitude, latLng.longitude));
            googleMap.setOnInfoWindowClickListener(marker -> {
                LatLng pos = marker.getPosition();
                openInGoogleMaps(pos.latitude, pos.longitude);
            });

            if (mapContainer != null) {
                final double finalLat = latitude;
                final double finalLng = longitude;
                mapContainer.setOnClickListener(v -> openInGoogleMaps(finalLat, finalLng));
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        this.googleMap = gMap;
        updateMapIfReady();
    }

    private void openInGoogleMaps(double lat, double lng) {
        String uri = "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(Reported+Location)";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        if (intent.resolveActivity(getPackageManager()) == null) {
            String web = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lng;
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(web));
        }
        startActivity(intent);
    }

    private void openFullScreenImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_fullscreen_image);

        ImageView fullImage = dialog.findViewById(R.id.full_image);
        Glide.with(this).load(imageUrl).into(fullImage);

        fullImage.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
    }

    // Helper to safely read a string from DocumentSnapshot
    private String safeGetString(DocumentSnapshot doc, String key) {
        Object o = doc.get(key);
        if (o instanceof String) return (String) o;
        return (o != null) ? o.toString() : null;
    }

    // Helper to read Double safely
    private Double getDoubleSafe(DocumentSnapshot doc, String key) {
        Object o = doc.get(key);
        if (o instanceof Double) return (Double) o;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            if (o != null) return Double.valueOf(o.toString());
        } catch (NumberFormatException ignored) {}
        return null;
    }
}
