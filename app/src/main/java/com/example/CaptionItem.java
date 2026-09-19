package com.example;

/**
 * Data model representing an entry in the live speech captions / conversation feed.
 */
public class CaptionItem {

    public static final String SENDER_USER = "YOU";
    public static final String SENDER_ZAVA = "ZAVA";

    private final String sender;
    private String text;
    private boolean isLive;
    private final long timestamp;

    public CaptionItem(String sender, String text, boolean isLive) {
        this.sender = sender;
        this.text = text;
        this.isLive = isLive;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isLive() {
        return isLive;
    }

    public void setLive(boolean live) {
        isLive = live;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
