package com.example.ecolens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Chat activity using modern WindowInsets handling (no deprecated setSoftInputMode).
 */
public class Chat extends AppCompatActivity {
    private RecyclerView recyclerView;
    private EditText messageInput;
    private Button sendButton;
    private ChatAdapter chatAdapter;
    private ArrayList<ChatMessage> chatMessages;
    private FirebaseFirestore db;
    private ListenerRegistration chatListener;

    // Reply UI and state
    private View replyPreviewContainer;
    private TextView replyPreviewUsername;
    private TextView replyPreviewText;
    private ImageView replyCancelButton;
    private ChatMessage replyingToMessage = null;
    private int replyingToPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We'll manage window insets ourselves
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_chat);

        // Root layout (CoordinatorLayout) from your activity_chat.xml
        View root = findViewById(R.id.rootLayout);
        View inputCard = findViewById(R.id.input_card);
        recyclerView = findViewById(R.id.recyclerView);

        // Modern: listen for both system bars and IME insets and apply padding/translation
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            // system bars (status + nav)
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // ime (keyboard)
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            // Always keep bottom padding equal to navigation bar (so input card is above nav)
            v.setPadding(0, 0, 0, sysBars.bottom);

            // If IME is visible (ime.bottom > 0), move the input card up by the IME height.
            // We animate the translation for smoothness.
            if (inputCard != null) {
                float targetY = -ime.bottom; // negative translation moves up
                inputCard.animate().translationY(targetY).setDuration(120).start();
            }

            // Ensure recycler view has a bottom inset so last messages stay above IME/input
            if (recyclerView != null) {
                recyclerView.setPadding(0, 0, 0, ime.bottom);
            }

            // Return the insets unchanged (we handled them)
            return insets;
        });

        // Close button
        ImageButton btn_close = findViewById(R.id.btn_close);
        btn_close.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        // Views (messageInput and sendButton)
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        // Reply preview views (optional in layout)
        replyPreviewContainer = findViewById(R.id.reply_preview_container);
        replyPreviewUsername = findViewById(R.id.reply_preview_username);
        replyPreviewText = findViewById(R.id.reply_preview_text);
        replyCancelButton = findViewById(R.id.reply_preview_cancel);

        if (replyPreviewContainer != null) replyPreviewContainer.setVisibility(View.GONE);
        if (replyCancelButton != null) {
            replyCancelButton.setOnClickListener(v -> clearReplyState());
        }

        // Initialize Firestore and message list
        db = FirebaseFirestore.getInstance();
        chatMessages = new ArrayList<>();

        // Get current user id (may be null if not signed in)
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        // Adapter with long-press listener (delete/copy)
        chatAdapter = new ChatAdapter(chatMessages, (message, position) -> onMessageLongPressed(message, position), currentUid);

        // RecyclerView setup
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // start from bottom
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);

        // Attach swipe-to-reply (right swipe)
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                ChatMessage msg = chatAdapter.getMessage(pos);
                if (msg != null) {
                    startReplyTo(msg, pos);
                }
                // restore visual state (we're not deleting)
                chatAdapter.notifyItemChanged(pos);
            }
        };
        new ItemTouchHelper(simpleCallback).attachToRecyclerView(recyclerView);

        // Load messages and listen for realtime updates
        loadMessages();

        // Send button
        sendButton.setOnClickListener(v -> sendMessage());
    }

    /**
     * Realtime listener for chatroom collection.
     * Handles ADDED, REMOVED, MODIFIED events.
     */
    private void loadMessages() {
        chatListener = db.collection("chatroom")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(Chat.this, "Error loading messages", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshots == null) return;

                    boolean addedAny = false;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        ChatMessage chatMessage = dc.getDocument().toObject(ChatMessage.class);
                        chatMessage.setId(dc.getDocument().getId());

                        switch (dc.getType()) {
                            case ADDED:
                                chatAdapter.addMessage(chatMessage);
                                addedAny = true;
                                break;
                            case REMOVED:
                                chatAdapter.removeMessageById(dc.getDocument().getId());
                                break;
                            case MODIFIED:
                                // Simple approach: remove old and re-add modified
                                chatAdapter.removeMessageById(dc.getDocument().getId());
                                chatAdapter.addMessage(chatMessage);
                                addedAny = true;
                                break;
                        }
                    }

                    // Auto-scroll if new messages arrived
                    if (addedAny && chatAdapter.getItemCount() > 0) {
                        recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                });
    }

    /**
     * Send a message to Firestore. If replyingToMessage is not null,
     * include reply metadata.
     */
    private void sendMessage() {

        String messageText = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) return;

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to send messages", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String displayName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
        String fallbackPhoto =
                FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl().toString()
                        : null;

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {

                    String username = "Anonymous";
                    String photoUrl = fallbackPhoto;

                    if (doc.exists()) {
                        if (doc.contains("name"))
                            username = doc.getString("name");

                        if (doc.contains("photoUrl"))
                            photoUrl = doc.getString("photoUrl");
                    } else if (displayName != null && !displayName.isEmpty()) {
                        username = displayName;
                    }

                    Map<String, Object> chatMessage = new HashMap<>();
                    chatMessage.put("message", messageText);
                    chatMessage.put("timestamp", System.currentTimeMillis());
                    chatMessage.put("uid", uid);
                    chatMessage.put("username", username);
                    if (photoUrl != null) chatMessage.put("photoUrl", photoUrl);

                    if (replyingToMessage != null) {
                        chatMessage.put("replyToId", replyingToMessage.getId());
                        chatMessage.put("replyToUsername", replyingToMessage.getUsername());
                        chatMessage.put("replyToText", replyingToMessage.getMessage());
                    }

                    db.collection("chatroom")
                            .add(chatMessage)
                            .addOnSuccessListener(ref -> {
                                messageInput.setText("");
                                clearReplyState();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(Chat.this, "Error sending message", Toast.LENGTH_SHORT).show()
                            );
                });
    }


    /**
     * Delete / Copy flow on long press.
     * Shows a dialog with options: Copy (always) and Delete (only for owner).
     */
    private void onMessageLongPressed(ChatMessage message, int position) {
        if (message == null) return;

        boolean isSignedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        String currentUid = isSignedIn ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        boolean isOwner = message.getUid() != null && message.getUid().equals(currentUid);

        // Build options list
        ArrayList<String> options = new ArrayList<>();
        options.add("Copy message");
        if (isOwner) options.add("Delete message");
        options.add("Cancel");

        CharSequence[] optsArray = options.toArray(new CharSequence[0]);

        new AlertDialog.Builder(Chat.this)
                .setTitle("Message options")
                .setItems(optsArray, (dialog, which) -> {
                    String chosen = options.get(which);
                    if ("Copy message".equals(chosen)) {
                        // Copy to clipboard
                        String textToCopy = message.getMessage() != null ? message.getMessage() : "";
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData clip = ClipData.newPlainText("Chat message", textToCopy);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(Chat.this, "Message copied to clipboard", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(Chat.this, "Unable to access clipboard", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("Delete message".equals(chosen)) {
                        // confirm delete
                        if (!isSignedIn) {
                            Toast.makeText(Chat.this, "You must be signed in to delete messages", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new AlertDialog.Builder(Chat.this)
                                .setTitle("Delete message")
                                .setMessage("Are you sure you want to delete this message?")
                                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                                .setPositiveButton("Delete", (d, w) -> {
                                    String messageId = message.getId();
                                    if (messageId == null || messageId.isEmpty()) {
                                        Toast.makeText(Chat.this, "Unable to delete message (no id)", Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    db.collection("chatroom").document(messageId)
                                            .delete()
                                            .addOnSuccessListener(aVoid -> {
                                                // local removal will also be handled by snapshot listener, but remove immediately for snappy UI
                                                Toast.makeText(Chat.this, "Message deleted", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> Toast.makeText(Chat.this, "Failed to delete message", Toast.LENGTH_SHORT).show());
                                })
                                .show();
                    } else {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /**
     * Start reply flow: show preview UI (if available) or prefill input.
     */
    private void startReplyTo(ChatMessage message, int position) {
        replyingToMessage = message;
        replyingToPosition = position;

        if (replyPreviewContainer != null && replyPreviewUsername != null && replyPreviewText != null) {
            replyPreviewContainer.setVisibility(View.VISIBLE);
            replyPreviewUsername.setText(message.getUsername() != null ? message.getUsername() : "Unknown");
            String preview = message.getMessage() != null ? message.getMessage() : "";
            if (preview.length() > 120) preview = preview.substring(0, 120) + "…";
            replyPreviewText.setText(preview);
        } else {
            // fallback: prefill input with @username
            String prefill = "@" + (message.getUsername() != null ? message.getUsername() : "") + " ";
            messageInput.setText(prefill);
            messageInput.setSelection(prefill.length());
        }
    }

    private void clearReplyState() {
        replyingToMessage = null;
        replyingToPosition = -1;
        if (replyPreviewContainer != null) replyPreviewContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
    }
}
