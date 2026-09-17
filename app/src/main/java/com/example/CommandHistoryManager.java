package com.example;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages local persistence of command execution history.
 */
public class CommandHistoryManager {

    private static final String PREF_NAME = "zava_command_history";
    private static final String KEY_ITEMS = "history_items";
    private static final int MAX_ITEMS = 100;

    private final SharedPreferences prefs;

    public CommandHistoryManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void recordCommand(String command, String intent, boolean isSuccess, String message) {
        List<HistoryItem> items = getHistory();
        HistoryItem newItem = new HistoryItem(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                command,
                intent,
                isSuccess,
                message
        );
        items.add(0, newItem); // Add at top

        if (items.size() > MAX_ITEMS) {
            items = items.subList(0, MAX_ITEMS);
        }

        saveItems(items);
    }

    public synchronized List<HistoryItem> getHistory() {
        List<HistoryItem> list = new ArrayList<>();
        String raw = prefs.getString(KEY_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(HistoryItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public synchronized void clearHistory() {
        prefs.edit().remove(KEY_ITEMS).apply();
    }

    private void saveItems(List<HistoryItem> items) {
        JSONArray arr = new JSONArray();
        for (HistoryItem item : items) {
            arr.put(item.toJson());
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply();
    }
}
