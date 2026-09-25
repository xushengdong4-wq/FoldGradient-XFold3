package com.lucas.foldgradient;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.hardware.display.DisplayManager;

public class FoldEffectService extends Service implements SensorEventListener, DisplayManager.DisplayListener {
    public static final String ACTION_START = "com.lucas.foldgradient.START";
    public static final String ACTION_PREVIEW = "com.lucas.foldgradient.PREVIEW";

    private static final String CHANNEL_ID = "fold_gradient_service";
    private static final int NOTIFICATION_ID = 7301;
    // X Fold3 reports about 175 degrees when physically flat. Stop a few degrees
    // before that so the translucent overlay can never remain on the open screen.
    private static final float EFFECT_END_ANGLE = 171f;
    private static final long OVERLAY_SAFETY_TIMEOUT_MS = 1000L;

    private SensorManager sensorManager;
    private Sensor hingeSensor;
    private DisplayManager displayManager;
    private WindowManager windowManager;
    private GradientOverlayView overlayView;
    private boolean overlayAttached = false;
    private boolean wasInnerDisplay = false;
    private float lastAngle = Float.NaN;
    private float smoothedAngle = Float.NaN;
    private boolean hingeDataReceived = false;
    private boolean openingMotion = false;
    private ValueAnimator fallbackAnimator;
    private final Handler displayPollHandler = new Handler(Looper.getMainLooper());
    private final Runnable overlaySafetyDetach = this::detachOverlay;
    private final Runnable displayPoll = new Runnable() {
        @Override
        public void run() {
            handleDisplayStateChange(isInnerDisplayActive());
            displayPollHandler.postDelayed(this, 120L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        sensorManager = getSystemService(SensorManager.class);
        displayManager = getSystemService(DisplayManager.class);
        windowManager = getSystemService(WindowManager.class);
        hingeSensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);

        if (sensorManager != null && hingeSensor != null) {
            sensorManager.registerListener(this, hingeSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        if (displayManager != null) {
            displayManager.registerDisplayListener(this, null);
        }
        wasInnerDisplay = isInnerDisplayActive();
        displayPollHandler.post(displayPoll);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean enabled = getSharedPreferences("fold_gradient", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (intent == null && !enabled) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_PREVIEW.equals(intent.getAction())) {
            runFallbackAnimation();
            if (!enabled) {
                new Handler(Looper.getMainLooper()).postDelayed(this::stopSelf, 1100L);
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    private void ensureOverlay() {
        if (overlayAttached || windowManager == null) return;
        overlayView = new GradientOverlayView(this);
        overlayView.setEffect(1f, 0f);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0);
        }
        windowManager.addView(overlayView, lp);
        overlayAttached = true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_HINGE_ANGLE || event.values.length == 0) return;
        float angle = event.values[0];
        hingeDataReceived = true;

        // The hardware's flat value is ~175 degrees rather than 180 degrees.
        if (angle >= EFFECT_END_ANGLE) {
            lastAngle = angle;
            smoothedAngle = angle;
            openingMotion = false;
            hideOverlay();
            return;
        }

        if (Float.isNaN(smoothedAngle)) smoothedAngle = angle;
        smoothedAngle = smoothedAngle * 0.72f + angle * 0.28f;

        if (Float.isNaN(lastAngle)) {
            lastAngle = smoothedAngle;
            return;
        }

        float delta = smoothedAngle - lastAngle;
        lastAngle = smoothedAngle;

        if (delta < -0.35f) {
            openingMotion = false;
            hideOverlay();
            return;
        }

        if (delta > 0.10f) {
            openingMotion = true;
            if (isInnerDisplayActive()) {
                cancelFallback();
                showForHingeAngle(smoothedAngle);
            }
        } else if (smoothedAngle >= EFFECT_END_ANGLE) {
            hideOverlay();
        }
    }

    private void showForHingeAngle(float angle) {
        float progress = clamp(angle / EFFECT_END_ANGLE);
        float remaining = 1f - progress;
        // Keep the effect visible through most of the opening motion, then let it melt away near flat.
        float intensity = clamp((float) (Math.pow(remaining, 0.58) * 1.75));
        if (angle >= EFFECT_END_ANGLE) intensity = 0f;
        if (intensity <= 0.002f) {
            detachOverlay();
            return;
        }
        ensureOverlay();
        if (!overlayAttached) return;
        overlayView.setEffect(progress, intensity);
        displayPollHandler.removeCallbacks(overlaySafetyDetach);
        displayPollHandler.postDelayed(overlaySafetyDetach, OVERLAY_SAFETY_TIMEOUT_MS);
    }

    private void runFallbackAnimation() {
        cancelFallback();
        ensureOverlay();
        if (!overlayAttached) return;
        fallbackAnimator = ValueAnimator.ofFloat(0f, 1f);
        fallbackAnimator.setDuration(680L);
        fallbackAnimator.setInterpolator(new DecelerateInterpolator(1.35f));
        fallbackAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float intensity = (1f - t) * 0.88f;
            overlayView.setEffect(0.55f + 0.45f * t, intensity);
        });
        fallbackAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) detachOverlay();
                if (fallbackAnimator == animation) fallbackAnimator = null;
            }
        });
        fallbackAnimator.start();
        displayPollHandler.removeCallbacks(overlaySafetyDetach);
        displayPollHandler.postDelayed(overlaySafetyDetach, OVERLAY_SAFETY_TIMEOUT_MS);
    }

    private void cancelFallback() {
        if (fallbackAnimator != null) {
            fallbackAnimator.cancel();
            fallbackAnimator = null;
        }
    }

    private void hideOverlay() {
        cancelFallback();
        detachOverlay();
    }

    private void detachOverlay() {
        displayPollHandler.removeCallbacks(overlaySafetyDetach);
        if (overlayAttached && windowManager != null && overlayView != null) {
            try {
                windowManager.removeViewImmediate(overlayView);
            } catch (Throwable ignored) { }
        }
        overlayAttached = false;
        overlayView = null;
    }

    private boolean isInnerDisplayActive() {
        try {
            int width;
            int height;
            if (windowManager != null && Build.VERSION.SDK_INT >= 30) {
                Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
                width = bounds.width();
                height = bounds.height();
            } else {
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                width = dm.widthPixels;
                height = dm.heightPixels;
            }
            float longSide = Math.max(width, height);
            float shortSide = Math.max(1, Math.min(width, height));
            float ratio = longSide / shortSide;
            // X Fold3 outer display is phone-like; inner display is close to square.
            return ratio < 1.60f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onDisplayAdded(int displayId) { }

    @Override
    public void onDisplayRemoved(int displayId) { }

    @Override
    public void onDisplayChanged(int displayId) {
        Display d = displayManager == null ? null : displayManager.getDisplay(displayId);
        if (d != null && d.getState() == Display.STATE_OFF) return;
        handleDisplayStateChange(isInnerDisplayActive());
    }

    private void handleDisplayStateChange(boolean nowInner) {
        if (nowInner && !wasInnerDisplay) {
            // A real hinge stream must always win. The old age-based fallback could
            // replay after the sensor stopped at 175 degrees and leave a seam behind.
            if (hingeSensor == null || !hingeDataReceived) {
                runFallbackAnimation();
            } else if (openingMotion && !Float.isNaN(lastAngle) && lastAngle < EFFECT_END_ANGLE) {
                showForHingeAngle(lastAngle);
            }
        }
        if (!nowInner && wasInnerDisplay) {
            hideOverlay();
        }
        wasInnerDisplay = nowInner;
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fold Gradient",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the fold-opening gradient effect ready in the background.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("Fold Gradient 已启用")
                .setContentText("展开内屏时叠加渐变，不更换当前壁纸")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        displayPollHandler.removeCallbacks(displayPoll);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (displayManager != null) displayManager.unregisterDisplayListener(this);
        cancelFallback();
        detachOverlay();
    }
}
