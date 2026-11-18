package ai.magpie.android;

import android.animation.ValueAnimator;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

final class GlassOptions {
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("host", Context.MODE_PRIVATE);
    }

    static boolean glassEnabled(Context context) {
        return prefs(context).getBoolean("glassEnabled", true);
    }

    static boolean reduceTransparency(Context context) {
        return prefs(context).getBoolean("reduceTransparency", false);
    }

    static boolean reduceMotion(Context context) {
        return prefs(context).getBoolean("reduceMotion", false);
    }

    static void setGlassEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("glassEnabled", enabled).apply();
    }

    static void setReduceTransparency(Context context, boolean reduced) {
        prefs(context).edit().putBoolean("reduceTransparency", reduced).apply();
    }

    static void setReduceMotion(Context context, boolean reduced) {
        prefs(context).edit().putBoolean("reduceMotion", reduced).apply();
    }

    static boolean stillMotion(Context context) {
        return reduceMotion(context) || !ValueAnimator.areAnimatorsEnabled();
    }

    static boolean solidSurface(Context context) {
        if (!glassEnabled(context) || reduceTransparency(context)) return true;
        // The system high-contrast text setting is available on earlier Android releases too.
        try {
            if (Settings.Secure.getInt(context.getContentResolver(), "high_text_contrast_enabled", 0) != 0) return true;
        } catch (SecurityException ignored) {
            // Some builds restrict reads of this setting to system applications.
        }
        try {
            if (Settings.Global.getInt(context.getContentResolver(), "disable_window_blurs", 0) != 0) return true;
        } catch (SecurityException ignored) {
            // The public contrast API below is independent of settings-provider access.
        }
        return Build.VERSION.SDK_INT >= 34 && Contrast34.increased(context);
    }

    private static final class Contrast34 {
        static boolean increased(Context context) {
            UiModeManager mode = context.getSystemService(UiModeManager.class);
            return mode != null && mode.getContrast() > 0f;
        }
    }

    private GlassOptions() {}
}
