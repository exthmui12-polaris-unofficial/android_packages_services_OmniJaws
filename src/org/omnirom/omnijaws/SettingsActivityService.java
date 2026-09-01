/*
 *  Copyright (C) 2017 The OmniROM Project
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import org.omnirom.omnijaws.client.OmniJawsClient;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivityService extends PreferenceActivity implements OnPreferenceChangeListener, WeatherLocationTask.Callback  {

    private static final String CHRONUS_ICON_PACK_INTENT = "com.dvtonder.chronus.ICON_PACK";
    private static final String DEFAULT_WEATHER_ICON_PACKAGE = "org.omnirom.omnijaws";

    private SharedPreferences mPrefs;
    private ListPreference mProvider;
    private CheckBoxPreference mCustomLocation;
    private ListPreference mUnits;
    private SwitchPreference mEnable;
    private boolean mTriggerUpdate;
    private boolean mTriggerPermissionCheck;
    private ListPreference mUpdateInterval;
    private CustomLocationPreference mLocation;
    private ListPreference mWeatherIconPack;
    private EditTextPreference mOwmApiKey;
    private SwitchPreference mBackgroundLocation;
    private SwitchPreference mLockscreenWeather;
    private ListPreference mLockscreenWeatherStyle;
    private Preference mUpdateStatus;
    private Handler mHandler = new Handler();
    private boolean mRequestBackgroundAfterForeground;
    private boolean mLaunchedBackgroundPermissionSettings;
    protected boolean mShowIconPack;

    private static final String PREF_KEY_CUSTOM_LOCATION_CITY = "weather_custom_location_city";
    private static final int PERMISSIONS_REQUEST_FOREGROUND_LOCATION = 0;
    private static final int PERMISSIONS_REQUEST_BACKGROUND_LOCATION = 1;
    private static final String WEATHER_ICON_PACK = "weather_icon_pack";
    private static final String PREF_KEY_UPDATE_STATUS = "update_status";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        /*if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }*/
        doLoadPreferences();
    }

    private void doLoadPreferences() {
        addPreferencesFromResource(R.xml.settings);

	//Remove those ugly dividers!!!
	ListView lv = getListView();
        lv.setDivider(new ColorDrawable(Color.TRANSPARENT));
        lv.setDividerHeight(0);

        final PreferenceScreen prefScreen = getPreferenceScreen();
        mEnable = (SwitchPreference) findPreference(Config.PREF_KEY_ENABLE);

        mCustomLocation = (CheckBoxPreference) findPreference(Config.PREF_KEY_CUSTOM_LOCATION);

        mProvider = (ListPreference) findPreference(Config.PREF_KEY_PROVIDER);
        mProvider.setOnPreferenceChangeListener(this);
        int idx = mProvider.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_PROVIDER,
                Config.DEFAULT_PROVIDER));
        if (idx == -1) {
            idx = 0;
        }
        mProvider.setValueIndex(idx);
        mProvider.setSummary(mProvider.getEntries()[idx]);

        mUnits = (ListPreference) findPreference(Config.PREF_KEY_UNITS);
        mUnits.setOnPreferenceChangeListener(this);
        idx = mUnits.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_UNITS, "0"));
        if (idx == -1) {
            idx = 0;
        }
        mUnits.setValueIndex(idx);
        mUnits.setSummary(mUnits.getEntries()[idx]);

        mUpdateInterval = (ListPreference) findPreference(Config.PREF_KEY_UPDATE_INTERVAL);
        mUpdateInterval.setOnPreferenceChangeListener(this);
        idx = mUpdateInterval.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_UPDATE_INTERVAL,
                Config.DEFAULT_UPDATE_INTERVAL));
        if (idx == -1) {
            idx = 0;
        }
        mUpdateInterval.setValueIndex(idx);
        mUpdateInterval.setSummary(mUpdateInterval.getEntries()[idx]);

        mLocation = (CustomLocationPreference) findPreference(PREF_KEY_CUSTOM_LOCATION_CITY);
        if (mPrefs.getBoolean(Config.PREF_KEY_ENABLE, false)
                && !mPrefs.getBoolean(Config.PREF_KEY_CUSTOM_LOCATION, false)) {
            mTriggerUpdate = false;
            checkLocationEnabled();
        }
        mWeatherIconPack = (ListPreference) findPreference(WEATHER_ICON_PACK);

        if (mShowIconPack) {
            String settingHeaderPackage = Config.getIconPack(this);
            List<String> entries = new ArrayList<String>();
            List<String> values = new ArrayList<String>();
            getAvailableWeatherIconPacks(entries, values);
            mWeatherIconPack.setEntries(entries.toArray(new String[entries.size()]));
            mWeatherIconPack.setEntryValues(values.toArray(new String[values.size()]));

            int valueIndex = mWeatherIconPack.findIndexOfValue(settingHeaderPackage);
            if (valueIndex == -1) {
                // no longer found
                settingHeaderPackage = Config.DEFAULT_ICON_PACK;
                Config.setIconPack(this, settingHeaderPackage);
                valueIndex = mWeatherIconPack.findIndexOfValue(settingHeaderPackage);
            }
            mWeatherIconPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
            mWeatherIconPack.setSummary(mWeatherIconPack.getEntry());
            mWeatherIconPack.setOnPreferenceChangeListener(this);
        } else {
            prefScreen.removePreference(mWeatherIconPack);
        }

        mOwmApiKey = (EditTextPreference) findPreference(Config.PREF_KEY_OWM_API_KEY);
        if (mOwmApiKey != null) {
            updateOwmApiKeySummary();
            mOwmApiKey.setOnPreferenceChangeListener(this);
        }

        mBackgroundLocation = (SwitchPreference) findPreference(
                Config.PREF_KEY_BACKGROUND_LOCATION);
        if (mBackgroundLocation != null) {
            mBackgroundLocation.setChecked(hasBackgroundLocationPermission());
            mBackgroundLocation.setOnPreferenceChangeListener(this);
        }

        mLockscreenWeather = (SwitchPreference) findPreference(
                Config.PREF_KEY_LOCKSCREEN_ENABLED);
        mLockscreenWeatherStyle = (ListPreference) findPreference(
                Config.PREF_KEY_LOCKSCREEN_STYLE);
        if (mLockscreenWeather != null) {
            mLockscreenWeather.setOnPreferenceChangeListener(this);
        }
        if (mLockscreenWeatherStyle != null) {
            int styleIndex = mLockscreenWeatherStyle.findIndexOfValue(
                    Config.getLockscreenWeatherStyle(this));
            mLockscreenWeatherStyle.setValueIndex(styleIndex >= 0 ? styleIndex : 0);
            mLockscreenWeatherStyle.setSummary(mLockscreenWeatherStyle.getEntry());
            mLockscreenWeatherStyle.setEnabled(mLockscreenWeather != null
                    && mLockscreenWeather.isChecked());
            mLockscreenWeatherStyle.setOnPreferenceChangeListener(this);
        }
        updateOwmPreferenceVisibility();
        mUpdateStatus = findPreference(PREF_KEY_UPDATE_STATUS);
        queryLastUpdateTime();
    }

    @Override
    public void onResume() {
        super.onResume();
        // values can be changed from outside
        getPreferenceScreen().removeAll();
        doLoadPreferences();
        if (mTriggerPermissionCheck) {
            checkLocationPermissions();
            mTriggerPermissionCheck = false;
        }
        if (mLaunchedBackgroundPermissionSettings) {
            mLaunchedBackgroundPermissionSettings = false;
            if (mBackgroundLocation != null) {
                mBackgroundLocation.setChecked(hasBackgroundLocationPermission());
            }
            if (hasBackgroundLocationPermission() && Config.isEnabled(this)
                    && !Config.isCustomLocation(this) && isProviderReady()) {
                WeatherService.scheduleUpdate(this);
            }
            Config.notifySettingsChanged(this);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mCustomLocation) {
            if (!mCustomLocation.isChecked()) {
                mTriggerUpdate = true;
                checkLocationEnabled();
            } else {
                if (Config.isEnabled(this) && isProviderReady()
                        && Config.getLocationName(this) != null) {
                    // city ids are provider specific - so we need to recheck
                    // cause provider migth be changed while unchecked
                    new WeatherLocationTask(this, Config.getLocationName(this), this).execute();
                }
            }
            Config.notifySettingsChanged(this);
            return true;
        } else if (preference == mEnable) {
            if (mEnable.isChecked()) {
                if (!mCustomLocation.isChecked()) {
                    mTriggerUpdate = true;
                    checkLocationEnabled();
                } else if (isProviderReady() && !TextUtils.isEmpty(
                        Config.getLocationId(this))) {
                    WeatherService.scheduleUpdate(this);
                }
            } else {
                disableService();
            }
            Config.notifySettingsChanged(this);
            queryLastUpdateTime();
            return true;
        } else if (preference == mUpdateStatus) {
            if (Config.isEnabled(this)) {
                WeatherService.startUpdate(this);
            }
            queryLastUpdateTime();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mProvider) {
            String value = (String) newValue;
            int idx = mProvider.findIndexOfValue(value);
            mProvider.setSummary(mProvider.getEntries()[idx]);
            mProvider.setValueIndex(idx);
            updateOwmPreferenceVisibility();
            boolean providerReady = Config.isProviderReady(this, value);
            if (Config.isEnabled(this) && providerReady && mCustomLocation.isChecked()
                    && Config.getLocationName(this) != null) {
                // city ids are provider specific - so we need to recheck
                new WeatherLocationTask(this, Config.getLocationName(this), this).execute();
            } else if (Config.isEnabled(this) && providerReady) {
                WeatherService.scheduleUpdate(this);
            } else if (!providerReady) {
                WeatherService.cancelUpdate(this);
            }
            notifySettingsChangedAfterCommit();
            return true;
        } else if (preference == mUnits) {
            String value = (String) newValue;
            int idx = mUnits.findIndexOfValue(value);
            mUnits.setSummary(mUnits.getEntries()[idx]);
            mUnits.setValueIndex(idx);
            if (Config.isEnabled(this)) {
                WeatherService.startUpdate(this);
            }
            notifySettingsChangedAfterCommit();
            return true;
        } else if (preference == mUpdateInterval) {
            String value = (String) newValue;
            int idx = mUpdateInterval.findIndexOfValue(value);
            mUpdateInterval.setSummary(mUpdateInterval.getEntries()[idx]);
            mUpdateInterval.setValueIndex(idx);
            if (Config.isEnabled(this)) {
                WeatherService.scheduleUpdate(this);
            }
            notifySettingsChangedAfterCommit();
            queryLastUpdateTime();
            return true;
        } else if (preference == mWeatherIconPack) {
            String value = (String) newValue;
            Config.setIconPack(this, value);
            int valueIndex = mWeatherIconPack.findIndexOfValue(value);
            mWeatherIconPack.setSummary(mWeatherIconPack.getEntries()[valueIndex]);
            notifySettingsChangedAfterCommit();
            return true;
        } else if (preference == mOwmApiKey) {
            String apiKey = String.valueOf(newValue).trim();
            updateOwmApiKeySummary(apiKey);
            if (Config.isEnabled(this) && mProvider != null && "0".equals(mProvider.getValue())) {
                // EditTextPreference commits after this callback returns.
                mHandler.postDelayed(() -> {
                    if (TextUtils.isEmpty(apiKey)) {
                        WeatherService.cancelUpdate(this);
                    } else {
                        WeatherService.scheduleUpdate(this);
                    }
                }, 200);
            }
            notifySettingsChangedAfterCommit();
            return true;
        } else if (preference == mBackgroundLocation) {
            boolean requested = Boolean.TRUE.equals(newValue);
            if (requested) {
                if (!hasForegroundLocationPermission()) {
                    mRequestBackgroundAfterForeground = true;
                    checkLocationEnabled();
                } else {
                    requestBackgroundLocation();
                }
            } else if (hasBackgroundLocationPermission()) {
                // Runtime permissions cannot be revoked by the app. Open this app's
                // settings and reflect the actual grant when the user returns.
                openAppPermissionSettings();
            }
            // This non-persistent switch represents the actual system grant; never let
            // Preference persist a requested state before Android grants it.
            return false;
        } else if (preference == mLockscreenWeather) {
            boolean enabled = Boolean.TRUE.equals(newValue);
            if (mLockscreenWeatherStyle != null) {
                mLockscreenWeatherStyle.setEnabled(enabled);
            }
            notifySettingsChangedAfterCommit();
            return true;
        } else if (preference == mLockscreenWeatherStyle) {
            String value = String.valueOf(newValue);
            int valueIndex = mLockscreenWeatherStyle.findIndexOfValue(value);
            mLockscreenWeatherStyle.setValueIndex(valueIndex);
            mLockscreenWeatherStyle.setSummary(mLockscreenWeatherStyle.getEntry());
            notifySettingsChangedAfterCommit();
            return true;
        }
        return false;
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Dialog dialog;

        // Build and show the dialog
        builder.setTitle(R.string.weather_retrieve_location_dialog_title);
        builder.setMessage(R.string.weather_retrieve_location_dialog_message);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mTriggerPermissionCheck = true;
                        mTriggerUpdate = true;
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.playstore:
                launchPlaystore();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void launchPlaystore() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://search?q=Chronus+icons&c=apps"));
        startActivity(intent);
    }

    private void checkLocationPermissions() {
        if (!hasForegroundLocationPermission()) {
            // Android 12 presents the approximate/precise choice for this combined request.
            requestPermissions(new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSIONS_REQUEST_FOREGROUND_LOCATION);
            return;
        }
        if (mTriggerUpdate) {
            mTriggerUpdate = false;
            if (hasBackgroundLocationPermission()) {
                WeatherService.scheduleUpdate(this);
            } else {
                if (Config.isEnabled(this) && isProviderReady()) {
                    // The activity is visible, so a one-shot foreground update can run.
                    // Do not create the periodic alarm until background access is granted.
                    WeatherService.startUpdate(this);
                }
                mRequestBackgroundAfterForeground = true;
            }
        }
        if (mRequestBackgroundAfterForeground) {
            mRequestBackgroundAfterForeground = false;
            requestBackgroundLocation();
        }
    }

    private boolean hasForegroundLocationPermission() {
        return Config.hasForegroundLocationPermission(this);
    }

    private boolean hasBackgroundLocationPermission() {
        return Config.hasBackgroundLocationPermission(this);
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocationPermission()) {
            if (mBackgroundLocation != null) {
                mBackgroundLocation.setChecked(true);
            }
            return;
        }
        if (!hasForegroundLocationPermission()) {
            mRequestBackgroundAfterForeground = true;
            checkLocationEnabled();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.background_location_title)
                .setMessage(R.string.location_permission_explanation)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        openAppPermissionSettings();
                    } else {
                        requestPermissions(
                                new String[] { Manifest.permission.ACCESS_BACKGROUND_LOCATION },
                                PERMISSIONS_REQUEST_BACKGROUND_LOCATION);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAppPermissionSettings() {
        mLaunchedBackgroundPermissionSettings = true;
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private boolean doCheckLocationEnabled() {
        return Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, -1) != Settings.Secure.LOCATION_MODE_OFF;
    }

    private void checkLocationEnabled() {
        if (!doCheckLocationEnabled()) {
            showDialog();
        } else {
            checkLocationPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_FOREGROUND_LOCATION: {
                boolean granted = false;
                for (int result : grantResults) {
                    granted |= result == PackageManager.PERMISSION_GRANTED;
                }
                if (granted) {
                    if (mTriggerUpdate) {
                        mTriggerUpdate = false;
                        if (hasBackgroundLocationPermission()) {
                            WeatherService.scheduleUpdate(this);
                        } else {
                            if (Config.isEnabled(this) && isProviderReady()) {
                                WeatherService.startUpdate(this);
                            }
                            mRequestBackgroundAfterForeground = true;
                        }
                    }
                    if (mRequestBackgroundAfterForeground) {
                        mRequestBackgroundAfterForeground = false;
                        requestBackgroundLocation();
                    }
                } else {
                    mRequestBackgroundAfterForeground = false;
                    if (mBackgroundLocation != null) {
                        mBackgroundLocation.setChecked(false);
                    }
                }
                break;
            }
            case PERMISSIONS_REQUEST_BACKGROUND_LOCATION:
                if (mBackgroundLocation != null) {
                    mBackgroundLocation.setChecked(hasBackgroundLocationPermission());
                }
                Config.notifySettingsChanged(this);
                break;
        }
    }

    private void disableService() {
        // stop any pending
        WeatherService.cancelUpdate(this);
        WeatherService.stop(this);
    }

    private boolean isProviderReady() {
        String provider = mProvider == null ? Config.DEFAULT_PROVIDER : mProvider.getValue();
        if (provider == null) {
            provider = mPrefs.getString(Config.PREF_KEY_PROVIDER, Config.DEFAULT_PROVIDER);
        }
        return Config.isProviderReady(this, provider);
    }

    private void updateOwmPreferenceVisibility() {
        if (mOwmApiKey == null) {
            return;
        }
        String provider = mProvider == null ? Config.DEFAULT_PROVIDER : mProvider.getValue();
        if (provider == null) {
            provider = mPrefs.getString(Config.PREF_KEY_PROVIDER, Config.DEFAULT_PROVIDER);
        }
        mOwmApiKey.setEnabled("0".equals(provider));
    }

    private void updateOwmApiKeySummary() {
        updateOwmApiKeySummary(mOwmApiKey == null ? null : mOwmApiKey.getText());
    }

    private void updateOwmApiKeySummary(String value) {
        if (mOwmApiKey == null) {
            return;
        }
        mOwmApiKey.setSummary(TextUtils.isEmpty(value == null ? null : value.trim())
                ? R.string.owm_api_key_unset : R.string.owm_api_key_set);
    }

    private void notifySettingsChangedAfterCommit() {
        mHandler.post(() -> Config.notifySettingsChanged(this));
    }

    @Override
    public void applyLocation(WeatherInfo.WeatherLocation result) {
        Config.setLocationId(this, result.id);
        Config.setLocationName(this, result.city);
        mLocation.setText(result.city);
        mLocation.setSummary(result.city);
        if (Config.isEnabled(this) && isProviderReady()) {
            WeatherService.scheduleUpdate(this);
        }
        Config.notifySettingsChanged(this);
    }

    private void getAvailableWeatherIconPacks(List<String> entries, List<String> values) {
        Intent i = new Intent();
        PackageManager packageManager = getPackageManager();
        i.setAction("org.omnirom.WeatherIconPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                values.add(0, r.activityInfo.name);
            } else {
                values.add(r.activityInfo.name);
            }
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                entries.add(0, label);
            } else {
                entries.add(label);
            }
        }
        i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(CHRONUS_ICON_PACK_INTENT);
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            values.add(packageName + ".weather");
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            entries.add(label);
        }
    }

    private void queryLastUpdateTime() {
        final AsyncTask<Void, Void, Void> t = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onProgressUpdate(Void... values) {
            }
            @Override
            protected Void doInBackground(Void... params) {
                final String updateTime = getLastUpdateTime();
                SettingsActivityService.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUpdateStatus.setSummary(updateTime);
                    }
                });
                return null;
            }
        };
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                t.execute();
            }
        }, 2000);
    }

    private String getLastUpdateTime() {
        OmniJawsClient mWeatherClient = new OmniJawsClient(this);
        if (mWeatherClient.isOmniJawsEnabled()) {
            OmniJawsClient.WeatherInfo mWeatherData = null;
            try {
                mWeatherClient.queryWeather();
                mWeatherData = mWeatherClient.getWeatherInfo();
                if (mWeatherData != null) {
                    return mWeatherData.getLastUpdateTime();
                }
            } catch(Exception ignored) {
            }
        }
        return getResources().getString(R.string.service_disabled);
    }
}
