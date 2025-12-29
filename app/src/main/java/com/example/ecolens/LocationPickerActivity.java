package com.example.ecolens;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Marker selectedMarker;
    private LatLng selectedLocation;
    private Button btnConfirmLocation;
    private ImageButton btnCurrentLocation;
    private String pincode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setStatusBarColor(Color.parseColor("#081108"));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

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

        btnCurrentLocation = findViewById(R.id.btn_current_location);
        btnConfirmLocation = findViewById(R.id.btn_confirm_location);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Handle current location button click
        btnCurrentLocation.setOnClickListener(v -> {
            btnCurrentLocation.setImageResource(R.drawable.ic_current_location_selected); // Change icon
            btnCurrentLocation.setColorFilter(Color.parseColor("#8bb4f8")); // Change tint
            fetchCurrentLocation();
        });



        // Handle location confirmation
        btnConfirmLocation.setOnClickListener(v -> returnSelectedLocation());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Default location (India Gate, New Delhi)
        LatLng defaultLocation = new LatLng(28.6129, 77.2295);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12));

        mMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                btnCurrentLocation.setImageResource(R.drawable.ic_current_location_unselected); // Change icon
                btnCurrentLocation.setColorFilter(getResources().getColor(android.R.color.white)); // Change tint back
            }
        });


        // Allow user to tap on the map to pick a location
        mMap.setOnMapClickListener(latLng -> {
            if (selectedMarker != null) {
                selectedMarker.remove();
            }
            selectedMarker = mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
            selectedLocation = latLng;
        });
    }

    private void fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                updateMapLocation(currentLatLng);
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMapLocation(LatLng latLng) {
        if (mMap != null) {
            if (selectedMarker != null) {
                selectedMarker.remove();
            }
            selectedMarker = mMap.addMarker(new MarkerOptions().position(latLng).title("Current Location"));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
            selectedLocation = latLng;
        }
    }

    private void returnSelectedLocation() {
        if (selectedLocation == null) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show();
            return;
        }

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(selectedLocation.latitude, selectedLocation.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                pincode = addresses.get(0).getPostalCode();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to get pincode", Toast.LENGTH_SHORT).show();
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra("latitude", selectedLocation.latitude);
        resultIntent.putExtra("longitude", selectedLocation.longitude);
        resultIntent.putExtra("pincode", pincode);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
