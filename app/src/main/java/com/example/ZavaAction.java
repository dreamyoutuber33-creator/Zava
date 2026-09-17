package com.example;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Represents a single predefined, safe executable action in Hey Zava.
 */
public class ZavaAction implements Serializable {
    public static final String ACTION_OPEN_APP = "OPEN_APP";
    public static final String ACTION_OPEN_SETTINGS = "OPEN_SETTINGS";
    public static final String ACTION_ACCESSIBILITY_CLICK = "ACCESSIBILITY_CLICK";
    public static final String ACTION_TYPE_TEXT = "TYPE_TEXT";
    public static final String ACTION_SCROLL = "SCROLL";
    public static final String ACTION_BACK = "BACK";
    public static final String ACTION_HOME = "HOME";
    public static final String ACTION_RECENTS = "RECENTS";
    public static final String ACTION_READ_SCREEN = "READ_SCREEN";
    public static final String ACTION_SEARCH = "SEARCH";
    public static final String ACTION_MEDIA_CONTROL = "MEDIA_CONTROL";
    public static final String ACTION_VOLUME_CONTROL = "VOLUME_CONTROL";
    public static final String ACTION_DELAY = "DELAY";

    private String action;
    private String target;
    private String param;
    private boolean requiresConfirmation;

    public ZavaAction(String action, String target, String param) {
        this(action, target, param, false);
    }

    public ZavaAction(String action, String target, String param, boolean requiresConfirmation) {
        this.action = action;
        this.target = target != null ? target : "";
        this.param = param != null ? param : "";
        this.requiresConfirmation = requiresConfirmation;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("action", action);
            obj.put("target", target);
            obj.put("param", param);
            obj.put("requires_confirmation", requiresConfirmation);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static ZavaAction fromJson(JSONObject obj) {
        String action = obj.optString("action", ACTION_OPEN_APP);
        String target = obj.optString("target", "");
        String param = obj.optString("param", "");
        boolean conf = obj.optBoolean("requires_confirmation", false);
        return new ZavaAction(action, target, param, conf);
    }
}
