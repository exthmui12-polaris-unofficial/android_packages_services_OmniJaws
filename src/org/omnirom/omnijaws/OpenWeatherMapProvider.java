/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnirom.omnijaws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class OpenWeatherMapProvider extends AbstractWeatherProvider {
    private static final String TAG = "OpenWeatherMapProvider";

    private static final int FORECAST_DAYS = 5;
    private static final int FORECAST_ENTRY_LIMIT = 40;
    private static final String SELECTION_LOCATION = "lat=%f&lon=%f";
    private static final String SELECTION_ID = "id=%s";

    private static final String URL_LOCATION =
            "https://api.openweathermap.org/data/2.5/find?q=%s&mode=json&lang=%s&appid=%s";
    private static final String URL_WEATHER =
            "https://api.openweathermap.org/data/2.5/weather?%s&mode=json&units=%s&lang=%s&appid=%s";
    private static final String URL_FORECAST =
            "https://api.openweathermap.org/data/2.5/forecast?%s&mode=json&units=%s&lang=%s&cnt="
                    + FORECAST_ENTRY_LIMIT + "&appid=%s";

    private List<String> mKeys = new ArrayList<String>();
    private int mRequestNumber;
    private int sunrise;
    private int sunset;

    public OpenWeatherMapProvider(Context context) {
        super(context);
    }

    public List<WeatherInfo.WeatherLocation> getLocations(String input) {
        String apiKey = getAPIKey();
        if (TextUtils.isEmpty(apiKey)) {
            return null;
        }
        mRequestNumber++;
        String url = String.format(URL_LOCATION, Uri.encode(input), getLanguageCode(),
                Uri.encode(apiKey));
        String response = retrieve(url);
        if (response == null) {
            return null;
        }

        try {
            JSONArray jsonResults = new JSONObject(response).getJSONArray("list");
            ArrayList<WeatherInfo.WeatherLocation> results = new ArrayList<WeatherInfo.WeatherLocation>();
            int count = jsonResults.length();

            for (int i = 0; i < count; i++) {
                JSONObject result = jsonResults.getJSONObject(i);
                WeatherInfo.WeatherLocation location = new WeatherInfo.WeatherLocation();

                location.id = result.getString("id");
                location.city = result.getString("name");
                location.countryId = result.getJSONObject("sys").getString("country");
                results.add(location);
            }

            return results;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed location data");
        }

        return null;
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_ID, id);
        return handleWeatherRequest(selection, metric);
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_LOCATION,
                location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(selection, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection, boolean metric) {
        String apiKey = getAPIKey();
        if (TextUtils.isEmpty(apiKey)) {
            return null;
        }
        mRequestNumber++;
        String units = metric ? "metric" : "imperial";
        String locale = getLanguageCode();
        String conditionUrl = String.format(Locale.US, URL_WEATHER, selection, units, locale,
                Uri.encode(apiKey));
        String conditionResponse = retrieve(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }
        mRequestNumber++;
        String forecastUrl = String.format(Locale.US, URL_FORECAST, selection, units, locale,
                Uri.encode(apiKey));
        String forecastResponse = retrieve(forecastUrl);
        if (forecastResponse == null) {
            return null;
        }
        try {
            JSONObject conditions = new JSONObject(conditionResponse);
            JSONObject weather = conditions.getJSONArray("weather").getJSONObject(0);
            JSONObject conditionData = conditions.getJSONObject("main");
            JSONObject windData = conditions.getJSONObject("wind");
            sunrise = conditions.getJSONObject("sys").getInt("sunrise");
            sunset = conditions.getJSONObject("sys").getInt("sunset");
            JSONObject forecastData = new JSONObject(forecastResponse);
            JSONObject forecastCity = forecastData.optJSONObject("city");
            int timezoneOffset = forecastCity == null ? 0
                    : forecastCity.optInt("timezone", 0);
            ArrayList<DayForecast> forecasts = parseForecasts(
                    forecastData.getJSONArray("list"), metric, timezoneOffset);
            String localizedCityName = conditions.getString("name");
            float windSpeed = (float) windData.getDouble("speed");
            if (metric) {
                // speeds are in m/s so convert to our common metric unit km/h
                windSpeed *= 3.6f;
            }
            WeatherInfo w = new WeatherInfo(mContext, conditions.getString("id"), localizedCityName,
                    /* condition */ weather.getString("main"),
                    /* conditionCode */ mapConditionIconToCode(
                            weather.getString("icon"), weather.getInt("id")),
                    /* temperature */ sanitizeTemperature(conditionData.getDouble("temp"), metric),
                    /* humidity */ (float) conditionData.getDouble("humidity"),
                    /* wind */ windSpeed,
                    /* windDir */ windData.has("deg") ? windData.getInt("deg") : 0,
                    metric,
                    forecasts,
                    System.currentTimeMillis());

            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data");
        }

        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray forecasts, boolean metric,
            int timezoneOffset) throws JSONException {
        if (forecasts.length() == 0) {
            throw new JSONException("Empty forecasts array");
        }

        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        LinkedHashMap<String, ForecastAccumulator> daily = new LinkedHashMap<>();
        for (int i = 0; i < forecasts.length(); i++) {
            try {
                JSONObject forecast = forecasts.getJSONObject(i);
                JSONObject conditionData = forecast.getJSONObject("main");
                JSONObject data = forecast.getJSONArray("weather").getJSONObject(0);
                long localSeconds = forecast.getLong("dt") + timezoneOffset;
                String date = dayFormat.format(new Date(localSeconds * 1000L));
                ForecastAccumulator accumulator = daily.get(date);
                if (accumulator == null) {
                    if (daily.size() >= FORECAST_DAYS) {
                        break;
                    }
                    accumulator = new ForecastAccumulator(date);
                    daily.put(date, accumulator);
                }
                int localHour = (int) Math.floorMod(localSeconds, 24L * 60L * 60L) / 3600;
                accumulator.add(
                        sanitizeTemperature(conditionData.getDouble("temp_min"), metric),
                        sanitizeTemperature(conditionData.getDouble("temp_max"), metric),
                        data.getString("main"),
                        mapConditionIconToCode(data.optString("icon"), data.getInt("id")),
                        localHour);
            } catch (JSONException e) {
                Log.w(TAG, "Ignoring malformed forecast entry");
            }
        }

        ArrayList<DayForecast> result = new ArrayList<>(FORECAST_DAYS);
        for (ForecastAccumulator accumulator : daily.values()) {
            result.add(accumulator.toForecast(metric));
        }
        // Clients assume exactly five entries; fill only genuinely unavailable days.
        if (result.size() < FORECAST_DAYS) {
            for (int i = result.size(); i < FORECAST_DAYS; i++) {
                Log.w(TAG, "Missing forecast for day " + i + " creating dummy");
                DayForecast item = new DayForecast(
                        /* low */ 0,
                        /* high */ 0,
                        /* condition */ "",
                        /* conditionCode */ -1,
                        "NaN",
                        metric);
                result.add(item);
            }
        }
        return result;
    }

    private static final class ForecastAccumulator {
        private final String mDate;
        private float mLow = Float.POSITIVE_INFINITY;
        private float mHigh = Float.NEGATIVE_INFINITY;
        private String mCondition = "";
        private int mConditionCode = -1;
        private int mBestHourDistance = Integer.MAX_VALUE;

        ForecastAccumulator(String date) {
            mDate = date;
        }

        void add(float low, float high, String condition, int conditionCode, int localHour) {
            mLow = Math.min(mLow, low);
            mHigh = Math.max(mHigh, high);
            int hourDistance = Math.abs(localHour - 12);
            if (hourDistance < mBestHourDistance) {
                mBestHourDistance = hourDistance;
                mCondition = condition;
                mConditionCode = conditionCode;
            }
        }

        DayForecast toForecast(boolean metric) {
            return new DayForecast(mLow, mHigh, mCondition, mConditionCode, mDate, metric);
        }
    }

    // OpenWeatherMap sometimes returns temperatures in Kelvin even if we ask it
    // for deg C or deg F. Detect this and convert accordingly.
    private static float sanitizeTemperature(double value, boolean metric) {
        // threshold chosen to work for both C and F. 170 deg F is hotter
        // than the hottest place on earth.
        if (value > 170) {
            // K -> deg C
            value -= 273.15;
            if (!metric) {
                // deg C -> deg F
                value = (value * 1.8) + 32;
            }
        }
        return (float) value;
    }

    private static final HashMap<String, String> LANGUAGE_CODE_MAPPING = new HashMap<String, String>();
    static {
        LANGUAGE_CODE_MAPPING.put("bg-", "bg");
        LANGUAGE_CODE_MAPPING.put("de-", "de");
        LANGUAGE_CODE_MAPPING.put("es-", "sp");
        LANGUAGE_CODE_MAPPING.put("fi-", "fi");
        LANGUAGE_CODE_MAPPING.put("fr-", "fr");
        LANGUAGE_CODE_MAPPING.put("it-", "it");
        LANGUAGE_CODE_MAPPING.put("nl-", "nl");
        LANGUAGE_CODE_MAPPING.put("pl-", "pl");
        LANGUAGE_CODE_MAPPING.put("pt-", "pt");
        LANGUAGE_CODE_MAPPING.put("ro-", "ro");
        LANGUAGE_CODE_MAPPING.put("ru-", "ru");
        LANGUAGE_CODE_MAPPING.put("se-", "se");
        LANGUAGE_CODE_MAPPING.put("tr-", "tr");
        LANGUAGE_CODE_MAPPING.put("uk-", "ua");
        LANGUAGE_CODE_MAPPING.put("zh-CN", "zh_cn");
        LANGUAGE_CODE_MAPPING.put("zh-TW", "zh_tw");
    }
    private String getLanguageCode() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String selector = locale.getLanguage() + "-" + locale.getCountry();

        for (Map.Entry<String, String> entry : LANGUAGE_CODE_MAPPING.entrySet()) {
            if (selector.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "en";
    }

    private int mapConditionIconToCode(String icon, int conditionId) {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        boolean isDay = !TextUtils.isEmpty(icon)
                ? icon.endsWith("d")
                : (sunrise < nowSeconds && sunset > nowSeconds);
        if (isDay) {
            // First, use condition ID for specific cases
            switch (conditionId) {
                // Thunderstorms
                case 202:   // thunderstorm with heavy rain
                case 232:   // thunderstorm with heavy drizzle
                case 211:   // thunderstorm
                    return 4;
                case 212:   // heavy thunderstorm
                    return 3;
                case 221:   // ragged thunderstorm
                case 231:   // thunderstorm with drizzle
                case 201:   // thunderstorm with rain
                    return 38;
                case 230:   // thunderstorm with light drizzle
                case 200:   // thunderstorm with light rain
                case 210:   // light thunderstorm
                    return 37;

                // Drizzle
                case 300:    // light intensity drizzle
                case 301:    // drizzle
                case 302:    // heavy intensity drizzle
                case 310:    // light intensity drizzle rain
                case 311:    // drizzle rain
                case 312:    // heavy intensity drizzle rain
                case 313:    // shower rain and drizzle
                case 314:    // heavy shower rain and drizzle
                case 321:    // shower drizzle
                    return 9;

                // Rain
                case 500:    // light rain
                case 501:    // moderate rain
                case 520:    // light intensity shower rain
                case 521:    // shower rain
                case 531:    // ragged shower rain
                    return 11;
                case 502:    // heavy intensity rain
                case 503:    // very heavy rain
                case 504:    // extreme rain
                case 522:    // heavy intensity shower rain
                    return 12;
                case 511:    // freezing rain
                    return 10;

                // Snow
                case 600: case 620: return 14; // light snow
                case 601: case 621: return 16; // snow
                case 602: case 622: return 41; // heavy snow
                case 611: case 612: return 18; // sleet
                case 615: case 616: return 5;  // rain and snow

                // Atmosphere
                case 741:    // fog
                    return 20;
                case 711:    // smoke
                case 762:    // volcanic ash
                    return 22;
                case 701:    // mist
                case 721:    // haze
                    return 21;
                case 731:    // sand/dust whirls
                case 751:    // sand
                case 761:    // dust
                    return 19;
                case 771:    // squalls
                    return 23;
                case 781:    // tornado
                    return 0;

                // clouds
                case 800:     // clear sky
                    return 32;
                case 801:     // few clouds
                    return 34;
                case 802:     // scattered clouds
                    return 28;
                case 803:     // broken clouds
                case 804:     // overcast clouds
                    return 30;

                // Extreme
                case 900: return 0;  // tornado
                case 901: return 1;  // tropical storm
                case 902: return 2;  // hurricane
                case 903: return 25; // cold
                case 904: return 36; // hot
                case 905: return 24; // windy
                case 906: return 17; // hail
            }
        } else {
                    // First, use condition ID for specific cases
                    switch (conditionId) {
                        // Thunderstorms
                        case 202:   // thunderstorm with heavy rain
                        case 232:   // thunderstorm with heavy drizzle
                        case 211:   // thunderstorm
                            return 4;
                        case 212:   // heavy thunderstorm
                            return 3;
                        case 221:   // ragged thunderstorm
                        case 231:   // thunderstorm with drizzle
                        case 201:   // thunderstorm with rain
                            return 47;
                        case 230:   // thunderstorm with light drizzle
                        case 200:   // thunderstorm with light rain
                        case 210:   // light thunderstorm
                            return 45;

                        // Drizzle
                        case 300:    // light intensity drizzle
                        case 301:    // drizzle
                        case 302:    // heavy intensity drizzle
                        case 310:    // light intensity drizzle rain
                        case 311:    // drizzle rain
                        case 312:    // heavy intensity drizzle rain
                        case 313:    // shower rain and drizzle
                        case 314:    // heavy shower rain and drizzle
                        case 321:    // shower drizzle
                            return 9;

                        // Rain
                        case 500:    // light rain
                        case 501:    // moderate rain
                        case 520:    // light intensity shower rain
                        case 521:    // shower rain
                        case 531:    // ragged shower rain
                            return 11;
                        case 502:    // heavy intensity rain
                        case 503:    // very heavy rain
                        case 504:    // extreme rain
                        case 522:    // heavy intensity shower rain
                            return 12;
                        case 511:    // freezing rain
                            return 10;

                        // Snow
                        case 600: case 620: return 14; // light snow
                        case 601: case 621: return 16; // snow
                        case 602: case 622: return 41; // heavy snow
                        case 611: case 612: return 18; // sleet
                        case 615: case 616: return 5;  // rain and snow

                        // Atmosphere
                        case 741:    // fog
                            return 20;
                        case 711:    // smoke
                        case 762:    // volcanic ash
                            return 22;
                        case 701:    // mist
                        case 721:    // haze
                            return 21;
                        case 731:    // sand/dust whirls
                        case 751:    // sand
                        case 761:    // dust
                            return 19;
                        case 771:    // squalls
                            return 23;
                        case 781:    // tornado
                            return 0;

                        // clouds
                        case 800:     // clear sky
                            return 31;
                        case 801:     // few clouds
                            return 33;
                        case 802:     // scattered clouds
                            return 27;
                        case 803:     // broken clouds
                        case 804:     // overcast clouds
                            return 29;

                        // Extreme
                        case 900: return 0;  // tornado
                        case 901: return 1;  // tropical storm
                        case 902: return 2;  // hurricane
                        case 903: return 25; // cold
                        case 904: return 36; // hot
                        case 905: return 24; // windy
                        case 906: return 17; // hail
                    }
    } 

        return -1;
    }

    private String getAPIKey() {
        return Config.getOpenWeatherMapApiKey(mContext);
    }

    public boolean shouldRetry() {
        return false;
    }
}
