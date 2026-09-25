package com.lucas.foldgradient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = context.getSharedPreferences("fold_gradient", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (enabled && Settings.canDrawOverlays(context)) {
            Intent service = new Intent(context, FoldEffectService.class);
            service.setAction(FoldEffectService.ACTION_START);
            context.startForegroundService(service);
        }
    }
}
