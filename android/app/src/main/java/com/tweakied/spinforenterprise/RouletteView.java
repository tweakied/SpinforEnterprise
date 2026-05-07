package com.tweakied.spinforenterprise;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class RouletteView extends View {

    private static final int SEGMENTS = 10;
    private static final int APPLE_SEGMENT = 7;
    private static final float SEGMENT_ANGLE = 360f / SEGMENTS;

    // Rich gradient colors for alternating segments
    private static final int[][] SEGMENT_GRADIENT_COLORS = {
            {0xFF2D1B69, 0xFF1A0F3D}, // deep purple
            {0xFF0D1B2A, 0xFF1B2838}, // dark navy
            {0xFF2D1B69, 0xFF1A0F3D},
            {0xFF0D1B2A, 0xFF1B2838},
            {0xFF2D1B69, 0xFF1A0F3D},
            {0xFF0D1B2A, 0xFF1B2838},
            {0xFF2D1B69, 0xFF1A0F3D},
            {0xFF1A472A, 0xFF0F3D1F}, // green-tinted for apple segment
            {0xFF2D1B69, 0xFF1A0F3D},
            {0xFF0D1B2A, 0xFF1B2838},
    };

    private Paint segmentPaint;
    private Paint textPaint;
    private Paint borderPaint;
    private Paint outerRingPaint;
    private Paint innerGlowPaint;
    private Paint centerPaint;
    private Paint centerHighlightPaint;
    private Paint tickPaint;
    private Paint shadowPaint;
    private RectF oval;
    private RectF outerOval;

    public RouletteView(Context context) {
        super(context);
        init();
    }

    public RouletteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RouletteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        segmentPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);

        outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingPaint.setStyle(Paint.Style.STROKE);

        innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerGlowPaint.setStyle(Paint.Style.STROKE);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setStyle(Paint.Style.FILL);

        centerHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerHighlightPaint.setStyle(Paint.Style.FILL);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.FILL);

        oval = new RectF();
        outerOval = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float fullRadius = Math.min(cx, cy) - 8f;
        float outerRingWidth = fullRadius * 0.08f;
        float radius = fullRadius - outerRingWidth;

        // --- Outer shadow glow ---
        shadowPaint.setColor(0x00000000);
        shadowPaint.setShadowLayer(16f, 0, 4f, 0x80000000);
        canvas.drawCircle(cx, cy, fullRadius, shadowPaint);

        // --- Outer metallic ring ---
        outerOval.set(cx - fullRadius, cy - fullRadius, cx + fullRadius, cy + fullRadius);
        outerRingPaint.setStrokeWidth(outerRingWidth);
        outerRingPaint.setShader(new SweepGradient(cx, cy,
                new int[]{0xFF4A3580, 0xFF7B5EA7, 0xFFE94560, 0xFF7B5EA7, 0xFF4A3580,
                        0xFF7B5EA7, 0xFFE94560, 0xFF7B5EA7, 0xFF4A3580},
                null));
        canvas.drawCircle(cx, cy, fullRadius - outerRingWidth / 2f, outerRingPaint);

        // --- Outer ring highlight edge ---
        borderPaint.setStrokeWidth(1.5f);
        borderPaint.setColor(0x50FFFFFF);
        canvas.drawCircle(cx, cy, fullRadius, borderPaint);
        borderPaint.setColor(0x30FFFFFF);
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // --- Tick marks on outer ring ---
        tickPaint.setColor(0xCCFFFFFF);
        tickPaint.setStrokeWidth(2.5f);
        for (int i = 0; i < SEGMENTS * 2; i++) {
            float angle = (float) Math.toRadians(i * (360f / (SEGMENTS * 2)));
            float innerR = fullRadius - outerRingWidth * 0.85f;
            float outerR = fullRadius - outerRingWidth * 0.15f;
            if (i % 2 == 0) {
                // Major tick
                tickPaint.setStrokeWidth(3f);
                tickPaint.setColor(0xEEFFFFFF);
            } else {
                // Minor tick
                tickPaint.setStrokeWidth(1.5f);
                tickPaint.setColor(0x80FFFFFF);
                innerR = fullRadius - outerRingWidth * 0.7f;
            }
            float x1 = cx + innerR * (float) Math.cos(angle);
            float y1 = cy + innerR * (float) Math.sin(angle);
            float x2 = cx + outerR * (float) Math.cos(angle);
            float y2 = cy + outerR * (float) Math.sin(angle);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // --- Draw segments with gradients ---
        for (int i = 0; i < SEGMENTS; i++) {
            float startAngle = i * SEGMENT_ANGLE;
            float midAngle = (float) Math.toRadians(startAngle + SEGMENT_ANGLE / 2f);

            // Create radial gradient for each segment
            float gradX = cx + radius * 0.5f * (float) Math.cos(midAngle);
            float gradY = cy + radius * 0.5f * (float) Math.sin(midAngle);
            segmentPaint.setShader(new RadialGradient(gradX, gradY, radius * 0.8f,
                    SEGMENT_GRADIENT_COLORS[i][0], SEGMENT_GRADIENT_COLORS[i][1],
                    Shader.TileMode.CLAMP));
            canvas.drawArc(oval, startAngle, SEGMENT_ANGLE, true, segmentPaint);
        }

        // --- Draw segment dividers with glow ---
        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setStrokeWidth(2f);
        dividerPaint.setColor(0x60FFFFFF);
        Paint dividerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerGlowPaint.setStrokeWidth(4f);
        dividerGlowPaint.setColor(0x20FFFFFF);

        float centerRadius = radius * 0.18f;
        for (int i = 0; i < SEGMENTS; i++) {
            float angle = (float) Math.toRadians(i * SEGMENT_ANGLE);
            float x1 = cx + centerRadius * (float) Math.cos(angle);
            float y1 = cy + centerRadius * (float) Math.sin(angle);
            float x2 = cx + radius * (float) Math.cos(angle);
            float y2 = cy + radius * (float) Math.sin(angle);
            canvas.drawLine(x1, y1, x2, y2, dividerGlowPaint);
            canvas.drawLine(x1, y1, x2, y2, dividerPaint);
        }

        // --- Draw icons ---
        for (int i = 0; i < SEGMENTS; i++) {
            float iconAngle = i * SEGMENT_ANGLE + SEGMENT_ANGLE / 2f;
            float iconRadius = radius * 0.62f;
            float iconX = cx + (float) (iconRadius * Math.cos(Math.toRadians(iconAngle)));
            float iconY = cy + (float) (iconRadius * Math.sin(Math.toRadians(iconAngle)));

            if (i == APPLE_SEGMENT) {
                textPaint.setTextSize(44f);
                textPaint.setColor(Color.WHITE);
                textPaint.setShadowLayer(8f, 0, 0, 0x8000FF00);
                canvas.drawText("\uD83C\uDF4E", iconX, iconY + 16f, textPaint);
                textPaint.clearShadowLayer();
            } else {
                textPaint.setTextSize(32f);
                textPaint.setColor(0xFFE94560);
                textPaint.setShadowLayer(6f, 0, 0, 0x80E94560);
                canvas.drawText("\u2715", iconX, iconY + 12f, textPaint);
                textPaint.clearShadowLayer();
            }
        }

        // --- Inner ring glow ---
        innerGlowPaint.setStrokeWidth(3f);
        innerGlowPaint.setColor(0x30E94560);
        canvas.drawCircle(cx, cy, radius * 0.42f, innerGlowPaint);

        // --- Center hub with gradient ---
        float hubRadius = radius * 0.18f;
        centerPaint.setShader(new RadialGradient(cx, cy - hubRadius * 0.3f, hubRadius * 1.5f,
                new int[]{0xFF7B5EA7, 0xFF533483, 0xFF2D1B69},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, hubRadius, centerPaint);

        // Center hub highlight
        centerHighlightPaint.setShader(new RadialGradient(
                cx - hubRadius * 0.2f, cy - hubRadius * 0.3f, hubRadius * 0.8f,
                0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, hubRadius, centerHighlightPaint);

        // Center hub border
        borderPaint.setStrokeWidth(2f);
        borderPaint.setColor(0x60FFFFFF);
        borderPaint.setShader(null);
        canvas.drawCircle(cx, cy, hubRadius, borderPaint);

        // Center text
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(16f);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setShadowLayer(4f, 0, 1f, 0x80000000);
        canvas.drawText("SPIN", cx, cy + 6f, textPaint);
        textPaint.clearShadowLayer();
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }
}
