package com.lucas.foldgradient;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    private TextView permissionStatus;
    private TextView sensorStatus;
    private SensorManager sensorManager;
    private Sensor hingeSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensorManager = getSystemService(SensorManager.class);
        hingeSensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
    }

    private LinearLayout buildUi() {
        int pad = dp(22);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.TOP);
        root.setBackgroundColor(Color.rgb(248, 248, 252));

        TextView title = text("Fold Gradient · X Fold3", 26, true);
        TextView sub = text("不更换壁纸。只在展开内屏时叠加渐变，完全展开后自动消失。", 16, false);
        sub.setPadding(0, dp(8), 0, dp(20));
        permissionStatus = text("", 15, true);
        sensorStatus = text("", 15, false);
        sensorStatus.setPadding(0, dp(4), 0, dp(18));

        Button grant = button("1. 允许显示在其他应用上");
        grant.setOnClickListener(v -> openOverlaySettings());

        Button start = button("2. 启动开屏渐变");
        start.setOnClickListener(v -> startEffectService());

        Button preview = button("预览一次渐变效果");
        preview.setOnClickListener(v -> previewEffect());

        Button stop = button("停止效果");
        stop.setOnClickListener(v -> stopEffectService());

        TextView note = text(
                "工作方式：优先读取铰链角度。若 vivo 未开放连续角度，则自动使用内外屏切换作为触发器。\n\n" +
                "渐变层不接收触摸，不会挡住桌面操作；关闭折叠屏时不会播放。",
                14, false);
        note.setPadding(0, dp(18), 0, 0);

        root.addView(title);
        root.addView(sub);
        root.addView(permissionStatus);
        root.addView(sensorStatus);
        root.addView(grant, matchWrap());
        root.addView(start, matchWrap());
        root.addView(preview, matchWrap());
        root.addView(stop, matchWrap());
        root.addView(note);
        return root;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(25, 25, 32));
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openOverlaySettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void startEffectService() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        getSharedPreferences("fold_gradient", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", true).apply();
        Intent i = new Intent(this, FoldEffectService.class);
        i.setAction(FoldEffectService.ACTION_START);
        startForegroundService(i);
        refreshStatus();
    }

    private void previewEffect() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        Intent i = new Intent(this, FoldEffectService.class);
        i.setAction(FoldEffectService.ACTION_PREVIEW);
        startForegroundService(i);
    }

    private void stopEffectService() {
        getSharedPreferences("fold_gradient", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", false).apply();
        stopService(new Intent(this, FoldEffectService.class));
        refreshStatus();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (sensorManager != null && hingeSensor != null) {
            sensorManager.registerListener(this, hingeSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean enabled = getSharedPreferences("fold_gradient", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
        permissionStatus.setText("悬浮层权限：" + (overlay ? "已允许" : "未允许") +
                "   |   效果：" + (enabled ? "已启用" : "未启用"));
        if (hingeSensor == null) {
            sensorStatus.setText("铰链角度传感器：未检测到（将使用内屏切换触发）");
        } else {
            sensorStatus.setText("铰链角度传感器：已检测到，等待角度数据…");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HINGE_ANGLE && event.values.length > 0) {
            sensorStatus.setText(String.format(Locale.US,
                    "铰链角度传感器：已检测到   当前 %.1f°", event.values[0]));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
