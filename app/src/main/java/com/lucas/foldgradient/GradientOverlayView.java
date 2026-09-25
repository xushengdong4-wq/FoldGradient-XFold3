package com.lucas.foldgradient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public class GradientOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float foldProgress = 0.5f;
    private float intensity = 0f;

    public GradientOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setEffect(float foldProgress, float intensity) {
        this.foldProgress = clamp(foldProgress);
        this.intensity = clamp(intensity);
        setVisibility(this.intensity <= 0.002f ? INVISIBLE : VISIBLE);
        invalidate();
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int alphaColor(int color, float a) {
        int alpha = Math.round(255f * clamp(a));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (intensity <= 0.002f) return;

        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.55f;

        // Very light overall wash: underlying wallpaper/app always remains visible.
        LinearGradient wash = new LinearGradient(
                0, 0, w, h,
                new int[]{
                        alphaColor(Color.rgb(14, 21, 54), 0.10f * intensity),
                        alphaColor(Color.rgb(65, 45, 160), 0.08f * intensity),
                        alphaColor(Color.rgb(8, 18, 48), 0.10f * intensity)
                },
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(wash);
        canvas.drawRect(0, 0, w, h, paint);

        // Gradient width narrows and fades as the hinge approaches 180°.
        float spread = w * (0.20f + 0.42f * (1f - foldProgress));
        float radius = Math.max(w, h) * (0.36f + 0.18f * (1f - foldProgress));

        RadialGradient leftGlow = new RadialGradient(
                cx - spread * 0.30f, cy, radius,
                new int[]{
                        alphaColor(Color.rgb(255, 135, 214), 0.50f * intensity),
                        alphaColor(Color.rgb(126, 103, 255), 0.26f * intensity),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.40f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(leftGlow);
        canvas.drawRect(0, 0, w, h, paint);

        RadialGradient rightGlow = new RadialGradient(
                cx + spread * 0.30f, cy, radius,
                new int[]{
                        alphaColor(Color.rgb(91, 220, 255), 0.46f * intensity),
                        alphaColor(Color.rgb(84, 105, 255), 0.25f * intensity),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.42f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(rightGlow);
        canvas.drawRect(0, 0, w, h, paint);

        // A bright but soft crease glow that disappears completely when flat.
        float lineHalf = Math.max(8f, w * (0.055f * (1f - foldProgress) + 0.008f));
        LinearGradient crease = new LinearGradient(
                cx - lineHalf, 0, cx + lineHalf, 0,
                new int[]{
                        Color.TRANSPARENT,
                        alphaColor(Color.rgb(195, 168, 255), 0.18f * intensity),
                        alphaColor(Color.WHITE, 0.34f * intensity),
                        alphaColor(Color.rgb(139, 205, 255), 0.18f * intensity),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.24f, 0.5f, 0.76f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(crease);
        canvas.drawRect(cx - lineHalf, 0, cx + lineHalf, h, paint);

        paint.setShader(null);
    }
}
