package com.example;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the normalized intent, risk classification, and action steps of a voice command.
 */
public class ZavaCommand implements Serializable {

    public enum RiskLevel {
        LOW_RISK,
        MEDIUM_RISK,
        HIGH_RISK
    }

    // Supported Intents
    public static final String INTENT_OPEN_APP = "OPEN_APP";
    public static final String INTENT_CLOSE_APP = "CLOSE_APP";
    public static final String INTENT_OPEN_SETTINGS = "OPEN_SETTINGS";
    public static final String INTENT_SEARCH = "SEARCH";
    public static final String INTENT_TYPE_TEXT = "TYPE_TEXT";
    public static final String INTENT_TAP = "TAP";
    public static final String INTENT_SCROLL = "SCROLL";
    public static final String INTENT_BACK = "BACK";
    public static final String INTENT_HOME = "HOME";
    public static final String INTENT_RECENT_APPS = "RECENT_APPS";
    public static final String INTENT_MEDIA_CONTROL = "MEDIA_CONTROL";
    public static final String INTENT_VOLUME_CONTROL = "VOLUME_CONTROL";
    public static final String INTENT_BRIGHTNESS_CONTROL = "BRIGHTNESS_CONTROL";
    public static final String INTENT_WIFI_SETTINGS = "WIFI_SETTINGS";
    public static final String INTENT_BLUETOOTH_SETTINGS = "BLUETOOTH_SETTINGS";
    public static final String INTENT_AIRPLANE_MODE_SETTINGS = "AIRPLANE_MODE_SETTINGS";
    public static final String INTENT_CAMERA = "CAMERA";
    public static final String INTENT_READ_SCREEN = "READ_SCREEN";
    public static final String INTENT_MULTI_ACTION = "MULTI_ACTION";
    public static final String INTENT_CUSTOM_COMMAND = "CUSTOM_COMMAND";
    public static final String INTENT_UNKNOWN = "UNKNOWN";

    private String rawSpeech = "";
    private String normalizedSpeech = "";
    private String intent = INTENT_UNKNOWN;
    private String target = "";
    private float confidence = 1.0f;
    private boolean requiresConfirmation = false;
    private RiskLevel riskLevel = RiskLevel.LOW_RISK;
    private String conversationalResponse = "";
    private List<ZavaAction> steps = new ArrayList<>();

    public ZavaCommand() {}

    public String getRawSpeech() {
        return rawSpeech;
    }

    public void setRawSpeech(String rawSpeech) {
        this.rawSpeech = rawSpeech;
    }

    public String getNormalizedSpeech() {
        return normalizedSpeech;
    }

    public void setNormalizedSpeech(String normalizedSpeech) {
        this.normalizedSpeech = normalizedSpeech;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getConversationalResponse() {
        return conversationalResponse;
    }

    public void setConversationalResponse(String conversationalResponse) {
        this.conversationalResponse = conversationalResponse;
    }

    public List<ZavaAction> getSteps() {
        return steps;
    }

    public void setSteps(List<ZavaAction> steps) {
        this.steps = steps;
    }

    public void addStep(ZavaAction action) {
        this.steps.add(action);
    }

    public boolean isMultiStep() {
        return steps != null && steps.size() > 1;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("intent", intent);
            obj.put("target", target);
            obj.put("confidence", confidence);
            obj.put("requires_confirmation", requiresConfirmation);
            obj.put("risk_level", riskLevel.name());
            obj.put("response", conversationalResponse);
            JSONArray arr = new JSONArray();
            if (steps != null) {
                for (ZavaAction step : steps) {
                    arr.put(step.toJson());
                }
            }
            obj.put("steps", arr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }
}
