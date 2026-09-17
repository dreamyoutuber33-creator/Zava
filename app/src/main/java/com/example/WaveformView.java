package com.example;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Custom AMOLED Neon Audio Waveform visualizer.
 * Renders smooth animated multi-harmonic sinusoidal waves with neon glow
 * responsive to audio input RMS levels and assistant state.
 */
public class WaveformView extends View {

    public enum Mode {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING
    }

    private Paint wavePaint1;
    private Paint wavePaint2;
    private Paint wavePaint3;
    private Path wavePath1 = new Path();
    private Path wavePath2 = new Path();
    private Path wavePath3 = new Path();

    private Mode currentMode = Mode.IDLE;
    private float targetAmplitude = 0.15f;
    private float currentAmplitude = 0.15f;
    private float phase = 0f;

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        wavePaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint1.setStyle(Paint.Style.STROKE);
        wavePaint1.setStrokeWidth(5f);
        wavePaint1.setColor(Color.parseColor("#00F2FE")); // Neon Cyan

        wavePaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint2.setStyle(Paint.Style.STROKE);
        wavePaint2.setStrokeWidth(3.5f);
        wavePaint2.setColor(Color.parseColor("#9B51E0")); // Neon Purple

        wavePaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint3.setStyle(Paint.Style.STROKE);
        wavePaint3.setStrokeWidth(2.5f);
        wavePaint3.setColor(Color.parseColor("#4FACFE")); // Neon Blue
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
        switch (mode) {
            case IDLE:
                targetAmplitude = 0.12f;
                break;
            case LISTENING:
                targetAmplitude = 0.55f;
                break;
            case PROCESSING:
                targetAmplitude = 0.35f;
                break;
            case SPEAKING:
                targetAmplitude = 0.75f;
                break;
        }
        postInvalidateOnAnimation();
    }

    public void setAudioLevel(float level) {
        // level typically 0.0f to 1.0f
        float clamped = Math.max(0.05f, Math.min(1.0f, level));
        if (currentMode == Mode.LISTENING) {
            targetAmplitude = 0.2f + (clamped * 0.8f);
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float midY = height / 2f;

        // Smoothly interpolate amplitude
        currentAmplitude += (targetAmplitude - currentAmplitude) * 0.15f;

        float speed;
        switch (currentMode) {
            case PROCESSING:
                speed = 0.12f;
                break;
            case SPEAKING:
                speed = 0.09f;
                break;
            case LISTENING:
                speed = 0.07f;
                break;
            default:
                speed = 0.03f;
                break;
        }
        phase += speed;

        float maxAmp = midY * 0.85f * currentAmplitude;

        // Draw Wave 1 (Primary Cyan)
        drawWave(canvas, wavePaint1, wavePath1, width, midY, maxAmp, 1.2f, phase);

        // Draw Wave 2 (Secondary Purple)
        drawWave(canvas, wavePaint2, wavePath2, width, midY, maxAmp * 0.75f, 1.8f, phase + 1.2f);

        // Draw Wave 3 (Tertiary Blue)
        drawWave(canvas, wavePaint3, wavePath3, width, midY, maxAmp * 0.5f, 2.5f, phase + 2.4f);

        // Continually animate
        postInvalidateOnAnimation();
    }

    private void drawWave(Canvas canvas, Paint paint, Path path, int width, float midY, float amp, float freqMultiplier, float offset) {
        path.reset();
        path.moveTo(0, midY);

        int step = 4;
        for (int x = 0; x <= width; x += step) {
            // Window function to taper edges gracefully to zero at left and right
            float normX = (float) x / width;
            float window = (float) Math.sin(normX * Math.PI);

            float angle = (float) (2 * Math.PI * normX * freqMultiplier + offset);
            float y = midY + (float) Math.sin(angle) * amp * window;
            path.lineTo(x, y);
        }

        canvas.drawPath(path, paint);
    }
}
