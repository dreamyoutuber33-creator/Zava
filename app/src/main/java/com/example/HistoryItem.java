package com.example;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class HistoryItem implements Serializable {
    private long id;
    private long timestamp;
    private String command;
    private String intent;
    private boolean isSuccess;
    private String resultMessage;

    public HistoryItem(long id, long timestamp, String command, String intent, boolean isSuccess, String resultMessage) {
        this.id = id;
        this.timestamp = timestamp;
        this.command = command != null ? command : "";
        this.intent = intent != null ? intent : "";
        this.isSuccess = isSuccess;
        this.resultMessage = resultMessage != null ? resultMessage : "";
    }

    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getCommand() {
        return command;
    }

    public String getIntent() {
        return intent;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("timestamp", timestamp);
            obj.put("command", command);
            obj.put("intent", intent);
            obj.put("isSuccess", isSuccess);
            obj.put("resultMessage", resultMessage);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static HistoryItem fromJson(JSONObject obj) {
        return new HistoryItem(
                obj.optLong("id", System.currentTimeMillis()),
                obj.optLong("timestamp", System.currentTimeMillis()),
                obj.optString("command", ""),
                obj.optString("intent", ""),
                obj.optBoolean("isSuccess", true),
                obj.optString("resultMessage", "")
        );
    }
}
