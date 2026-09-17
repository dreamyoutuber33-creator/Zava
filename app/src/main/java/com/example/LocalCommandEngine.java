package com.example;

import android.content.Context;

/**
 * Local offline rule-based command planner guaranteeing full offline functionality.
 */
public class LocalCommandEngine implements AICommandEngine {

    private final CommandPlanner planner;

    public LocalCommandEngine(Context context, AppResolver appResolver) {
        this.planner = new CommandPlanner(context, appResolver);
    }

    @Override
    public void plan(String userInput, Callback callback) {
        try {
            ZavaCommand command = planner.planCommand(userInput);
            if (callback != null) {
                callback.onPlanReady(command);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onError("Local parsing error: " + e.getMessage());
            }
        }
    }
}
