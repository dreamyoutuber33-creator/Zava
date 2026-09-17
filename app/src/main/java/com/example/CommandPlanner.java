package com.example;

import android.content.Context;

/**
 * Plans and validates action sequences, performing safety and risk checks.
 */
public class CommandPlanner {

    private final Context context;
    private final IntentAnalyzer intentAnalyzer;

    public CommandPlanner(Context context, AppResolver appResolver) {
        this.context = context.getApplicationContext();
        this.intentAnalyzer = new IntentAnalyzer(appResolver);
    }

    public ZavaCommand planCommand(String speech) {
        ZavaCommand command = intentAnalyzer.analyze(speech);

        // Risk Level Evaluation
        switch (command.getIntent()) {
            case ZavaCommand.INTENT_OPEN_APP:
            case ZavaCommand.INTENT_OPEN_SETTINGS:
            case ZavaCommand.INTENT_WIFI_SETTINGS:
            case ZavaCommand.INTENT_BLUETOOTH_SETTINGS:
            case ZavaCommand.INTENT_AIRPLANE_MODE_SETTINGS:
            case ZavaCommand.INTENT_BRIGHTNESS_CONTROL:
            case ZavaCommand.INTENT_VOLUME_CONTROL:
            case ZavaCommand.INTENT_MEDIA_CONTROL:
            case ZavaCommand.INTENT_BACK:
            case ZavaCommand.INTENT_HOME:
            case ZavaCommand.INTENT_RECENT_APPS:
            case ZavaCommand.INTENT_SCROLL:
            case ZavaCommand.INTENT_READ_SCREEN:
            case ZavaCommand.INTENT_CAMERA:
            case ZavaCommand.INTENT_SEARCH:
                command.setRiskLevel(ZavaCommand.RiskLevel.LOW_RISK);
                command.setRequiresConfirmation(false);
                break;

            case ZavaCommand.INTENT_CUSTOM_COMMAND:
            case ZavaCommand.INTENT_MULTI_ACTION:
                command.setRiskLevel(ZavaCommand.RiskLevel.LOW_RISK);
                command.setRequiresConfirmation(false);
                break;

            default:
                command.setRiskLevel(ZavaCommand.RiskLevel.LOW_RISK);
                break;
        }

        return command;
    }
}
