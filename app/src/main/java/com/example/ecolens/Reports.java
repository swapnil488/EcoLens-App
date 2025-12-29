package com.example.ecolens;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class Reports extends AppCompatActivity {

    private LinearLayout reportsLayout;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private ImageButton btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reports);

        // Adjust padding to account for system windows.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnClose = findViewById(R.id.btn_close);
        reportsLayout = findViewById(R.id.reports_layout);
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Set close button behavior
        btnClose.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        if (currentUser == null) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadReports();
    }

    private void loadReports() {
        db.collection("reports").whereEqualTo("uid", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        addReportButton(doc);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(Reports.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void addReportButton(DocumentSnapshot doc) {
        // Create a CardView
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        // Set a top margin of 12dp (converted to pixels)
        int marginPx = (int) (12 * getResources().getDisplayMetrics().density);
        cardParams.setMargins(0, marginPx, 0, 0);
        cardView.setLayoutParams(cardParams);
        cardView.setCardElevation(8 * getResources().getDisplayMetrics().density);
        cardView.setRadius(12 * getResources().getDisplayMetrics().density);
        // Use color #1E1E1E for card background
        cardView.setCardBackgroundColor(Color.parseColor("#1E1E1E"));

        // Create the Button that will show the report title
        Button btnReport = new Button(this);
        // Use CardView.LayoutParams for the button inside the card
        CardView.LayoutParams btnParams = new CardView.LayoutParams(
                CardView.LayoutParams.MATCH_PARENT,
                CardView.LayoutParams.WRAP_CONTENT
        );
        btnReport.setLayoutParams(btnParams);
        btnReport.setBackgroundColor(Color.TRANSPARENT);
        btnReport.setText(doc.getString("title"));
        btnReport.setTextColor(Color.WHITE);
        btnReport.setAllCaps(false);
        btnReport.setTextSize(16);
        btnReport.setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable(R.drawable.ic_arrow_right), null);
        btnReport.setPadding(marginPx, marginPx, marginPx, marginPx);
        btnReport.setOnClickListener(v -> openReportDetails(doc.getId()));

        cardView.addView(btnReport);
        reportsLayout.addView(cardView);
    }

    private void openReportDetails(String reportId) {
        Intent intent = new Intent(this, ReportDetails.class);
        intent.putExtra("reportId", reportId);
        startActivity(intent);
    }
}
