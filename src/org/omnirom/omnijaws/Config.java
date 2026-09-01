/*
 *  Copyright (C) 2015 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnijaws;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;

public class Config {
    public static final String PREF_KEY_PROVIDER = "provider";
    public static final String PREF_KEY_UNITS = "units";
    public static final String PREF_KEY_LOCATION_ID = "location_id";
    public static final String PREF_KEY_LOCATION_NAME = "location_name";
    public static final String PREF_KEY_CUSTOM_LOCATION = "custom_location";
    public static final String PREF_KEY_WEATHER_DATA = "weather_data";
    public static final String PREF_KEY_LAST_UPDATE = "last_update";
    public static final String PREF_KEY_ENABLE = "enable";
    public static final String PREF_KEY_UPDATE_INTERVAL = "update_interval";
    public static final String PREF_KEY_ICON_PACK = "icon_pack";
    public static final String PREF_KEY_LAST_ALARM = "last_alarm";
    public static final String PREF_KEY_UPDATE_ERROR = "update_error";
    public static final String PREF_KEY_OWM_API_KEY = "owm_api_key";
    public static final String PREF_KEY_LOCKSCREEN_ENABLED = "lockscreen_weather_enabled";
    public static final String PREF_KEY_LOCKSCREEN_STYLE = "lockscreen_weather_style";
    public static final String PREF_KEY_BACKGROUND_LOCATION = "background_location";

    public static final String DEFAULT_PROVIDER = "1";
    public static final String DEFAULT_UNITS = "0";
    public static final String DEFAULT_UPDATE_INTERVAL = "2";
    public static final String DEFAULT_ICON_PACK = "org.omnirom.omnijaws.outline";
    public static final String LOCKSCREEN_STYLE_COMPACT = "compact";
    public static final String LOCKSCREEN_STYLE_SLICE = "slice";

    public static AbstractWeatherProvider getProvider(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        switch (prefs.getString(PREF_KEY_PROVIDER, DEFAULT_PROVIDER))
        {
            case "1":
                return new METNorwayProvider(context);
            case "2":
            case "3":
            case "4":
            case "5":
            case "7":
            case "8":
                // Legacy providers remain source-compatible but are not exposed by the
                // Android 12 UI. Use the key-free MET Norway provider for old values.
                return new METNorwayProvider(context);
            case "0":
                return new OpenWeatherMapProvider(context);
            default:
                return new METNorwayProvider(context);
        }
    }

    public static String getProviderId(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        String provider = prefs.getString(PREF_KEY_PROVIDER, DEFAULT_PROVIDER);
        switch (provider)
        {
            case "1":
                return "MET Norway";
            case "2":
            case "3":
            case "4":
            case "5":
            case "7":
            case "8":
                return "MET Norway";
            case "0":
                return "OpenWeatherMap";
            default:
                return "MET Norway";
        }
    }

    public static boolean isMetric(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return DEFAULT_UNITS.equals(prefs.getString(PREF_KEY_UNITS, DEFAULT_UNITS));
    }

    public static boolean isCustomLocation(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getBoolean(PREF_KEY_CUSTOM_LOCATION, false);
    }

    public static String getLocationId(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getString(PREF_KEY_LOCATION_ID, null);
    }

    public static void setLocationId(Context context, String id) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().putString(PREF_KEY_LOCATION_ID, id).commit();
    }
    
    public static String getLocationName(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getString(PREF_KEY_LOCATION_NAME, null);
    }
    
    public static void setLocationName(Context context, String name) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().putString(PREF_KEY_LOCATION_NAME, name).commit();
    }

    public static WeatherInfo getWeatherData(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        String str = prefs.getString(PREF_KEY_WEATHER_DATA, null);
        if (str != null) {
            WeatherInfo data = WeatherInfo.fromSerializedString(context, str);
            return data;
        }
        return null;
    }
    
    public static void setWeatherData(Context context, WeatherInfo data) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().putString(PREF_KEY_WEATHER_DATA, data.toSerializedString()).commit();
        prefs.edit().putLong(PREF_KEY_LAST_UPDATE, System.currentTimeMillis()).commit();
    }

    public static void clearWeatherData(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().remove(PREF_KEY_WEATHER_DATA).commit();
        prefs.edit().remove(PREF_KEY_LAST_UPDATE).commit();
    }
    
    public static long getLastUpdateTime(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getLong(PREF_KEY_LAST_UPDATE, 0);
    }

    public static void clearLastUpdateTime(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().putLong(PREF_KEY_LAST_UPDATE, 0).commit();
        prefs.edit().putLong(PREF_KEY_LAST_ALARM, 0).commit();
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getBoolean(PREF_KEY_ENABLE, false);
    }

    public static boolean setEnabled(Context context, boolean value) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.edit().putBoolean(PREF_KEY_ENABLE, value).commit();
    }

    public static int getUpdateInterval(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        String valueString = prefs.getString(PREF_KEY_UPDATE_INTERVAL,
                DEFAULT_UPDATE_INTERVAL);
        try {
            int value = Integer.parseInt(valueString);
            return value >= 1 && value <= 12 ? value : 2;
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public static String getIconPack(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getString(PREF_KEY_ICON_PACK, DEFAULT_ICON_PACK);
    }

    public static void setIconPack(Context context, String value) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().putString(PREF_KEY_ICON_PACK, value).commit();
    }

    public static long getLastAlarmTime(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getLong(PREF_KEY_LAST_ALARM, 0);
    }

    public static void setLastAlarmTime(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().putLong(PREF_KEY_LAST_ALARM, System.currentTimeMillis()).commit();
    }

    public static boolean isUpdateError(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        return prefs.getBoolean(PREF_KEY_UPDATE_ERROR, false);
    }

    public static void setUpdateError(Context context, boolean value) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        prefs.edit().putBoolean(PREF_KEY_UPDATE_ERROR, value).commit();
    }

    public static String getOpenWeatherMapApiKey(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = prefs.getString(PREF_KEY_OWM_API_KEY, "");
        return key == null ? "" : key.trim();
    }

    public static boolean isProviderReady(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return isProviderReady(context,
                prefs.getString(PREF_KEY_PROVIDER, DEFAULT_PROVIDER));
    }

    public static boolean isProviderReady(Context context, String provider) {
        return !"0".equals(provider) || !TextUtils.isEmpty(getOpenWeatherMapApiKey(context));
    }

    public static boolean hasForegroundLocationPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasBackgroundLocationPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canScheduleUpdates(Context context) {
        if (!isEnabled(context) || !isProviderReady(context)) {
            return false;
        }
        if (isCustomLocation(context)) {
            return !TextUtils.isEmpty(getLocationId(context))
                    && !TextUtils.isEmpty(getLocationName(context));
        }
        return hasForegroundLocationPermission(context)
                && hasBackgroundLocationPermission(context);
    }

    public static boolean isLockscreenWeatherEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return isEnabled(context) && prefs.getBoolean(PREF_KEY_LOCKSCREEN_ENABLED, false);
    }

    public static String getLockscreenWeatherStyle(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String value = prefs.getString(PREF_KEY_LOCKSCREEN_STYLE, LOCKSCREEN_STYLE_COMPACT);
        return LOCKSCREEN_STYLE_SLICE.equals(value) ? LOCKSCREEN_STYLE_SLICE
                : LOCKSCREEN_STYLE_COMPACT;
    }

    public static boolean isBackgroundLocationRequested(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_KEY_BACKGROUND_LOCATION, false);
    }

    public static void notifySettingsChanged(Context context) {
        context.getContentResolver().notifyChange(
                Uri.parse("content://" + WeatherContentProvider.AUTHORITY + "/settings"), null);
    }

    public static boolean isSetupDone(Context context) {
        // A manually selected city is a complete setup and must not require any
        // location permission. Automatic mode accepts either approximate or precise
        // foreground location on Android 12.
        if (isCustomLocation(context)
                && !TextUtils.isEmpty(getLocationId(context))
                && !TextUtils.isEmpty(getLocationName(context))) {
            return true;
        }
        return hasForegroundLocationPermission(context);
    }
}
