package com.example.ecolens;

public class Tip {
    private String tipText;
    private String imageUrl;
    private String sourceUrl;

    // Empty constructor required for Firestore
    public Tip() {
    }

    public Tip(String tipText, String imageUrl, String sourceUrl) {
        this.tipText = tipText;
        this.imageUrl = imageUrl;
        this.sourceUrl = sourceUrl;
    }

    public String getTipText() {
        return tipText;
    }

    public void setTipText(String tipText) {
        this.tipText = tipText;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
}
