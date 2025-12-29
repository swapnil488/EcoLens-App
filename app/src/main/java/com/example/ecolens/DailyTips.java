package com.example.ecolens;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class DailyTips extends AppCompatActivity {
    private static final String PREFS_NAME      = "DailyTipsPrefs";
    private static final String KEY_LAST_DATE   = "lastDate";    // left for compatibility (not required now)
    private static final String KEY_LAST_INDEX  = "lastIndex";   // left for compatibility (not required now)

    private RecyclerView rvTips;
    private FirebaseFirestore firestore;
    private List<Tip> allTips    = new ArrayList<>();
    private List<Tip> todaysTips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_daily_tips);

        // Handle window insets for full-screen
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // Close button
        ImageButton btnClose = findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        // RecyclerView setup
        rvTips = findViewById(R.id.rvTips);
        rvTips.setLayoutManager(new LinearLayoutManager(this));

        // Firestore instance
        firestore = FirebaseFirestore.getInstance();

        // Load tips from Firestore
        loadDailyTips();
    }

    private void loadDailyTips() {
        firestore.collection("tips")
                .get()
                .addOnSuccessListener(query -> {
                    allTips.clear();
                    todaysTips.clear();

                    if (!query.isEmpty()) {
                        // Ensure deterministic order for all clients: sort by document ID
                        List<DocumentSnapshot> docs = query.getDocuments();
                        Collections.sort(docs, Comparator.comparing(DocumentSnapshot::getId));

                        for (DocumentSnapshot doc : docs) {
                            Tip t = doc.toObject(Tip.class);
                            if (t != null) allTips.add(t);
                        }

                        // Now consult/create the daily meta doc and display today's tips
                        ensureMetaAndShowTodaysTips(query);
                    } else {
                        Toast.makeText(this, "No tips found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load tips.", Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Ensures a single meta document (meta/daily_tips) exists for today with:
     *  - date (yyyyMMdd)
     *  - startIndex
     *  - batchSize
     *  - nextStartIndex
     *
     * The code uses a transaction to create the meta only if today's meta isn't present.
     * After that we read meta and slice the tips list accordingly (same for all users).
     */
    private void ensureMetaAndShowTodaysTips(QuerySnapshot originalQuery) {
        final int total = allTips.size();
        if (total == 0) return;

        // 1. Get today's date as a string "yyyyMMdd"
        final String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(new Date());

        final DocumentReference metaRef = firestore.collection("meta").document("daily_tips");

        // Run a transaction that will create/initialize today's meta only if not present
        firestore.runTransaction(transaction -> {
            DocumentSnapshot metaSnap = transaction.get(metaRef);

            boolean needCreateOrUpdate = true;
            if (metaSnap.exists()) {
                String metaDate = metaSnap.getString("date");
                if (today.equals(metaDate)) {
                    // Today's meta already present — nothing to do
                    needCreateOrUpdate = false;
                }
            }

            if (needCreateOrUpdate) {
                // Determine startIndex based on stored nextStartIndex (if present) or lastIndex (if present)
                int prevNextIndex = 0;
                if (metaSnap.exists()) {
                    // Try to read nextStartIndex or startIndex fallback
                    if (metaSnap.contains("nextStartIndex")) {
                        Object o = metaSnap.get("nextStartIndex");
                        if (o instanceof Long) prevNextIndex = ((Long) o).intValue();
                        else if (o instanceof Integer) prevNextIndex = (Integer) o;
                    } else if (metaSnap.contains("startIndex")) {
                        Object o = metaSnap.get("startIndex");
                        if (o instanceof Long) prevNextIndex = ((Long) o).intValue();
                        else if (o instanceof Integer) prevNextIndex = (Integer) o;
                    }
                }

                int startIndex = prevNextIndex % total;

                // Random batch size between 10 and 15 (inclusive)
                int batchSize = new Random().nextInt(6) + 10;
                if (batchSize > total) batchSize = total; // clamp if not enough tips

                int nextStartIndex = (startIndex + batchSize) % total;

                Map<String, Object> metaMap = new HashMap<>();
                metaMap.put("date", today);
                metaMap.put("startIndex", startIndex);
                metaMap.put("batchSize", batchSize);
                metaMap.put("nextStartIndex", nextStartIndex);
                metaMap.put("lastUpdated", FieldValue.serverTimestamp());

                // Use merge so we don't accidentally wipe other meta fields
                transaction.set(metaRef, metaMap, SetOptions.merge());
            }

            return null;
        }).addOnSuccessListener(aVoid -> {
            // After transaction completes, fetch meta doc to get the definitive values
            metaRef.get().addOnSuccessListener(metaSnapshot -> {
                if (!metaSnapshot.exists()) {
                    Toast.makeText(this, "Failed to read daily tips meta.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String metaDate = metaSnapshot.getString("date");
                long startIndexLong = metaSnapshot.contains("startIndex") ? metaSnapshot.getLong("startIndex") : 0L;
                long batchSizeLong = metaSnapshot.contains("batchSize") ? metaSnapshot.getLong("batchSize") : 10L;

                int startIndex = (int) (startIndexLong % total);
                int batchSize = (int) Math.min(batchSizeLong, total);

                // Slice batchSize tips starting at startIndex (with wrap-around)
                todaysTips.clear();
                for (int i = 0; i < batchSize; i++) {
                    todaysTips.add(allTips.get((startIndex + i) % total));
                }

                // Show in RecyclerView
                rvTips.setAdapter(new TipAdapter(this, todaysTips));
            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Failed to read daily tips meta.", Toast.LENGTH_SHORT).show()
            );
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to initialize daily tips meta.", Toast.LENGTH_SHORT).show();
        });
    }
}
