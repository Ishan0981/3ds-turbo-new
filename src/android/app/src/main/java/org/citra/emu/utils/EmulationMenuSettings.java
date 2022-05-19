package org.citra.emu.utils;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.citra.emu.CitraApplication;
import org.citra.emu.overlay.InputOverlay;

public class EmulationMenuSettings {
    private static SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(CitraApplication.getAppContext());

    public static boolean getJoystickRelCenter() {
        return mPreferences.getBoolean(InputOverlay.PREF_JOYSTICK_RELATIVE, true);
    }

    public static boolean getDpadSlideEnable() {
        return mPreferences.getBoolean("EmulationMenuSettings_DpadSlideEnable", true);
    }

    public static int getLandscapeScreenLayout() {
        return mPreferences.getInt(InputOverlay.PREF_SCREEN_LAYOUT, 0);
    }

    public static boolean getSwapScreens() {
        return mPreferences.getBoolean("EmulationMenuSettings_SwapScreens", false);
    }

    public static void setSwapScreens(boolean value) {
        final SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean("EmulationMenuSettings_SwapScreens", value);
        editor.apply();
    }

    public static boolean getShowOverlay() {
        return mPreferences.getBoolean(InputOverlay.PREF_SHOW_OVERLAY, true);
    }
}
