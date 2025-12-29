package com.example.ecolens;

public class ChatMessage {

    private String id;
    private String message;
    private String username;
    private long timestamp;
    private String uid;
    private String photoUrl;   // ✅ NEW

    // Reply metadata
    private String replyToId;
    private String replyToUsername;
    private String replyToText;

    public ChatMessage() {}

    public String getId() { return id; }
    public String getMessage() { return message; }
    public String getUsername() { return username; }
    public long getTimestamp() { return timestamp; }
    public String getUid() { return uid; }
    public String getPhotoUrl() { return photoUrl; }

    public String getReplyToId() { return replyToId; }
    public String getReplyToUsername() { return replyToUsername; }
    public String getReplyToText() { return replyToText; }

    public void setId(String id) { this.id = id; }
    public void setMessage(String message) { this.message = message; }
    public void setUsername(String username) { this.username = username; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setUid(String uid) { this.uid = uid; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }
    public void setReplyToUsername(String replyToUsername) { this.replyToUsername = replyToUsername; }
    public void setReplyToText(String replyToText) { this.replyToText = replyToText; }
}
