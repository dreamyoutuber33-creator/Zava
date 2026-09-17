package com.example;

/**
 * Interface isolating AI command planning so the assistant functions
 * seamlessly in offline mode while supporting optional online AI.
 */
public interface AICommandEngine {

    interface Callback {
        void onPlanReady(ZavaCommand plannedCommand);
        void onError(String errorMessage);
    }

    void plan(String userInput, Callback callback);
}
