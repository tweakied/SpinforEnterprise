package com.tweakied.spinforenterprise;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class RouletteView extends View {

    private static final int SEGMENTS = 10;
    private static final int APPLE_SEGMENT = 7;
    private static final float SEGMENT_ANGLE = 360f / SEGMENTS;

    private static final int[] SEGMENT_COLORS = {
            0xFF1A1A2E, 0xFF16213E, 0xFF1A1A2E, 0xFF16213E, 0xFF1A1A2E,
            0xFF16213E, 0xFF1A1A2E, 0xFF0F3460, 0xFF1A1A2E, 0xFF16213E
    };

    private Paint segmentPaint;
    private Paint textPaint;
    private Paint borderPaint;
    private Paint centerPaint;
    private RectF oval;

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
        segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        segmentPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(0xFFE94560);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(0xFF533483);
        centerPaint.setStyle(Paint.Style.FILL);

        oval = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 20f;

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Draw segments
        for (int i = 0; i < SEGMENTS; i++) {
            float startAngle = i * SEGMENT_ANGLE;
            segmentPaint.setColor(SEGMENT_COLORS[i]);
            canvas.drawArc(oval, startAngle, SEGMENT_ANGLE, true, segmentPaint);

            // Draw icon in segment
            canvas.save();
            float iconAngle = startAngle + SEGMENT_ANGLE / 2f;
            float iconRadius = radius * 0.65f;
            float iconX = cx + (float) (iconRadius * Math.cos(Math.toRadians(iconAngle)));
            float iconY = cy + (float) (iconRadius * Math.sin(Math.toRadians(iconAngle)));

            if (i == APPLE_SEGMENT) {
                // Apple icon (emoji)
                textPaint.setTextSize(40f);
                canvas.drawText("🍎", iconX, iconY + 14f, textPaint);
            } else {
                // X icon
                textPaint.setTextSize(36f);
                textPaint.setColor(0xFFE94560);
                canvas.drawText("✕", iconX, iconY + 12f, textPaint);
                textPaint.setColor(Color.WHITE);
            }
            canvas.restore();
        }

        // Draw border
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // Draw inner border rings
        borderPaint.setStrokeWidth(2f);
        borderPaint.setColor(0x40FFFFFF);
        canvas.drawCircle(cx, cy, radius * 0.4f, borderPaint);
        borderPaint.setStrokeWidth(6f);
        borderPaint.setColor(0xFFE94560);

        // Draw center circle
        canvas.drawCircle(cx, cy, radius * 0.15f, centerPaint);
        textPaint.setTextSize(20f);
        canvas.drawText("SPIN", cx, cy + 7f, textPaint);

        // Draw segment dividers
        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(0x60FFFFFF);
        dividerPaint.setStrokeWidth(2f);
        for (int i = 0; i < SEGMENTS; i++) {
            float angle = (float) Math.toRadians(i * SEGMENT_ANGLE);
            float x1 = cx + radius * 0.15f * (float) Math.cos(angle);
            float y1 = cy + radius * 0.15f * (float) Math.sin(angle);
            float x2 = cx + radius * (float) Math.cos(angle);
            float y2 = cy + radius * (float) Math.sin(angle);
            canvas.drawLine(x1, y1, x2, y2, dividerPaint);
        }
    }
}
