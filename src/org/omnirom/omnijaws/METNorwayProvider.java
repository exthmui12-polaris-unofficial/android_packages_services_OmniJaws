// Author this class - vladrevers

package org.omnirom.omnijaws;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.util.Log;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import java.util.TimeZone;


public class METNorwayProvider extends AbstractWeatherProvider {
    private static final String TAG = "METNorwayProvider";

    private static final String URL_WEATHER =
            "https://api.met.no/weatherapi/locationforecast/2.0/?";
    private static final String PART_COORDINATES =
            "lat=%f&lon=%f";
    private static final String URL_PLACES =
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5"
                    + "&addressdetails=1&accept-language=%s&q=%s";
    private static final String URL_REVERSE =
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1"
                    + "&zoom=10&accept-language=%s&lat=%f&lon=%f";

    private final SimpleDateFormat mGmt0Format =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private final SimpleDateFormat mUserTimeZoneFormat =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private final SimpleDateFormat mDayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public METNorwayProvider(Context context) {
        super(context);
        initTimeZoneFormat();
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String coordinates = String.format(Locale.US, PART_COORDINATES, location.getLatitude(), location.getLongitude());
        return getAllWeather(coordinates, metric);
    }

    public List<WeatherInfo.WeatherLocation> getLocations(String input) {
        String lang = Locale.getDefault().getLanguage().replaceFirst("_", "-");
        String url = String.format(URL_PLACES, lang, Uri.encode(input));
        String response = retrieve(url);
        if (response == null) {
            return null;
        }
        try {
            JSONArray jsonResults = new JSONArray(response);
            ArrayList<WeatherInfo.WeatherLocation> results = new ArrayList<>(jsonResults.length());
            int count = jsonResults.length();

            for (int i = 0; i < count; i++) {
                JSONObject result = jsonResults.getJSONObject(i);
                WeatherInfo.WeatherLocation location = new WeatherInfo.WeatherLocation();

                double latitude = result.getDouble("lat");
                double longitude = result.getDouble("lon");
                if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    continue;
                }
                JSONObject address = result.optJSONObject("address");
                String city = firstNonEmpty(address, "city", "town", "village", "municipality",
                        "suburb", "county");
                if (TextUtils.isEmpty(city)) {
                    city = result.optString("display_name", input);
                }

                location.id = String.format(Locale.US, PART_COORDINATES, latitude, longitude);
                location.city = city;
                location.countryId = address == null ? ""
                        : address.optString("country_code", "").toUpperCase(Locale.US);
                location.country = address == null ? "" : address.optString("country", "");
                location.postal = address == null ? "" : address.optString("postcode", "");
                results.add(location);
            }

            return results;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed location data", e);
        }

        return null;
    }

    private static String firstNonEmpty(JSONObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        return getAllWeather(id, metric);
    }

    private WeatherInfo getAllWeather(String coordinates, boolean metric) {
        String url = URL_WEATHER + coordinates;
        String response = retrieve(url);
        if (response == null) {
            return null;
        }
        try {
            JSONArray timeseries = new JSONObject(response).getJSONObject("properties").getJSONArray("timeseries");
            JSONObject weather = timeseries.getJSONObject(0).getJSONObject("data").getJSONObject("instant").getJSONObject("details");

            double windSpeed = weather.getDouble("wind_speed");
            if (metric) {
                windSpeed *= 3.6;
            }

            String symbolCode = timeseries.getJSONObject(0).getJSONObject("data").getJSONObject("next_1_hours").getJSONObject("summary").getString("symbol_code");
            int weatherCode = arrayWeatherIconToCode[getPriorityCondition(symbolCode)];

            // Check Available Night Icon
            if(symbolCode.contains("_night") && (weatherCode == 30 || weatherCode == 32 || weatherCode == 34)) {
                weatherCode -= 1;
            }

            String city = getNameLocality(coordinates);
            if (TextUtils.isEmpty(city)) {
                city = mContext.getResources().getString(R.string.omnijaws_city_unknown);
            }

            WeatherInfo w = new WeatherInfo(mContext,
                    /* id */ coordinates,
                    /* cityId */ city,
                    /* condition */ symbolCode,
                    /* conditionCode */ weatherCode,
                    /* temperature */ convertTemperature(weather.getDouble("air_temperature"), metric),
                    /* humidity */ (float) weather.getDouble("relative_humidity"),
                    /* wind */ (float) windSpeed,
                    /* windDir */ (int) weather.getDouble("wind_from_direction"),
                    metric,
                    parseForecasts(timeseries, metric),
                    System.currentTimeMillis());

            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data", e);
        }

        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray timeseries, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<>(5);
        int count = timeseries.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());

        int whileIndex = 0;

        while (convertTimeZone(timeseries.getJSONObject(whileIndex).getString("time")).contains(yesterday)) {
            whileIndex++;
        }

        boolean endDay = (whileIndex == 0) && isEndDay(convertTimeZone(timeseries.getJSONObject(whileIndex).getString("time")));

        for (int i = 0; i < 5; i++) {
            DayForecast item;
            try {

                double temp_max = Double.MIN_VALUE;
                double temp_min = Double.MAX_VALUE;
                String day = getDay(i);
                int symbolCode = 0;
                int scSixToTwelve = 0; // symbolCode next_6_hours in 06:00
                int scTwelveToEighteen = 0; // symbolCode next_6_hours in 12:00
                int scSixToEighteen = 0; // symbolCode next_12_hours in 06:00
                boolean hasFastCondition = false;
                String conditionDescription = "";
                String cdSixToEighteen = ""; // SymbolCode in 06:00 or 12:00

                while (convertTimeZone(timeseries.getJSONObject(whileIndex).getString("time")).contains(day)) {
                    double tempI = timeseries.getJSONObject(whileIndex).getJSONObject("data").getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");

                    if (tempI > temp_max) {
                        temp_max = tempI;
                    }
                    if (tempI < temp_min) {
                        temp_min = tempI;
                    }

                    boolean hasOneHour = timeseries.getJSONObject(whileIndex).getJSONObject("data").has("next_1_hours");
                    boolean hasSixHours = timeseries.getJSONObject(whileIndex).getJSONObject("data").has("next_6_hours");
                    boolean hasTwelveHours = timeseries.getJSONObject(whileIndex).getJSONObject("data").has("next_12_hours");

                    hasFastCondition = scSixToEighteen != 0 || (scSixToTwelve != 0 && scTwelveToEighteen != 0);

                    if (!hasFastCondition && ((i == 0 && endDay) || isMorningOrAfternoon(convertTimeZone(timeseries.getJSONObject(whileIndex).getString("time")), hasOneHour))) {
                        String stepHours = hasOneHour ? "next_1_hours" : "next_6_hours";

                        String stepTextSymbolCode = timeseries.getJSONObject(whileIndex).getJSONObject("data").getJSONObject(stepHours).getJSONObject("summary").getString("symbol_code");
                        int stepSymbolCode = getPriorityCondition(stepTextSymbolCode);

                        if (stepSymbolCode > symbolCode) {
                            symbolCode = stepSymbolCode;
                            conditionDescription = stepTextSymbolCode;
                        }

                        if(hasSixHours || hasTwelveHours) {
                            if (convertTimeZone(timeseries.getJSONObject(whileIndex).getString("time")).contains("T06")) {
                                String textSymbolCode = timeseries.getJSONObject(whileIndex).getJSONObject("data").getJSONObject(hasTwelveHours ? "next_12_hours" : "next_6_hours").getJSONObject("summary").getString("symbol_code");
                                if (hasTwelveHours) {
                                    scSixToEighteen = getPriorityCondition(textSymbolCode);
                                    cdSixToEighteen = timeseries.getJSONObject(whileIndex).getJSONObject("data").getJSONObject("next_12_hours").getJSONObject("summary").getString("symbol_code");
                                } else {
                                    scSixToTwelve = getPriorityCondition(textSymbolCode);
                                    cdSixToEighteen = textSymbolCode;
                                }
                            } else if (scSixToTwelve != 0 && convertTimeZone(timeseries.getJSONObject(whileIndex).getString("time")).contains("T12")) {
                                String textSymbolCode = timeseries.getJSONObject(whileIndex).getJSONObject("data").getJSONObject("next_6_hours").getJSONObject("summary").getString("symbol_code");
                                scTwelveToEighteen = getPriorityCondition(textSymbolCode);

                                if (scSixToTwelve < scTwelveToEighteen) {
                                    cdSixToEighteen = textSymbolCode;
                                }
                            }
                        }
                    }
                    whileIndex++;
                }

                if(hasFastCondition) {
                    symbolCode = (scSixToEighteen != 0) ? scSixToEighteen : Math.max(scSixToTwelve, scTwelveToEighteen);
                    conditionDescription = cdSixToEighteen;
                }

                item = new DayForecast(
                        /* low */ convertTemperature(temp_min, metric),
                        /* high */ convertTemperature(temp_max, metric),
                        /* condition */ conditionDescription,
                        /* conditionCode */ arrayWeatherIconToCode[symbolCode],
                        day,
                        metric);
            } catch (JSONException e) {
                Log.w(TAG, "Invalid forecast for day " + i + " creating dummy", e);
                item = new DayForecast(
                        /* low */ 0,
                        /* high */ 0,
                        /* condition */ "",
                        /* conditionCode */ -1,
                        "NaN",
                        metric);
            }
            result.add(item);
        }
        // clients assume there are 5  entries - so fill with dummy if needed
        if (result.size() < 5) {
            for (int i = result.size(); i < 5; i++) {
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

    private static final HashMap<String, Integer> SYMBOL_CODE_MAPPING = new HashMap<>();
    static {
        SYMBOL_CODE_MAPPING.put("clearsky", 1);
        SYMBOL_CODE_MAPPING.put("fair", 2);
        SYMBOL_CODE_MAPPING.put("partlycloudy", 3);
        SYMBOL_CODE_MAPPING.put("cloudy", 4);
        SYMBOL_CODE_MAPPING.put("rainshowers", 5);
        SYMBOL_CODE_MAPPING.put("rainshowersandthunder", 6);
        SYMBOL_CODE_MAPPING.put("sleetshowers", 7);
        SYMBOL_CODE_MAPPING.put("snowshowers", 8);
        SYMBOL_CODE_MAPPING.put("rain", 9);
        SYMBOL_CODE_MAPPING.put("heavyrain", 10);
        SYMBOL_CODE_MAPPING.put("heavyrainandthunder", 11);
        SYMBOL_CODE_MAPPING.put("sleet", 12);
        SYMBOL_CODE_MAPPING.put("snow", 13);
        SYMBOL_CODE_MAPPING.put("snowandthunder", 14);
        SYMBOL_CODE_MAPPING.put("fog", 15);
        SYMBOL_CODE_MAPPING.put("sleetshowersandthunder", 20);
        SYMBOL_CODE_MAPPING.put("snowshowersandthunder", 21);
        SYMBOL_CODE_MAPPING.put("rainandthunder", 22);
        SYMBOL_CODE_MAPPING.put("sleetandthunder", 23);
        SYMBOL_CODE_MAPPING.put("lightrainshowersandthunder", 24);
        SYMBOL_CODE_MAPPING.put("heavyrainshowersandthunder", 25);
        SYMBOL_CODE_MAPPING.put("lightssleetshowersandthunder", 26);
        SYMBOL_CODE_MAPPING.put("heavysleetshowersandthunder", 27);
        SYMBOL_CODE_MAPPING.put("lightssnowshowersandthunder", 28);
        SYMBOL_CODE_MAPPING.put("heavysnowshowersandthunder", 29);
        SYMBOL_CODE_MAPPING.put("lightrainandthunder", 30);
        SYMBOL_CODE_MAPPING.put("lightsleetandthunder", 31);
        SYMBOL_CODE_MAPPING.put("heavysleetandthunder", 32);
        SYMBOL_CODE_MAPPING.put("lightsnowandthunder", 33);
        SYMBOL_CODE_MAPPING.put("heavysnowandthunder", 34);
        SYMBOL_CODE_MAPPING.put("lightrainshowers", 40);
        SYMBOL_CODE_MAPPING.put("heavyrainshowers", 41);
        SYMBOL_CODE_MAPPING.put("lightsleetshowers", 42);
        SYMBOL_CODE_MAPPING.put("heavysleetshowers", 43);
        SYMBOL_CODE_MAPPING.put("lightsnowshowers", 44);
        SYMBOL_CODE_MAPPING.put("heavysnowshowers", 45);
        SYMBOL_CODE_MAPPING.put("lightrain", 46);
        SYMBOL_CODE_MAPPING.put("lightsleet", 47);
        SYMBOL_CODE_MAPPING.put("heavysleet", 48);
        SYMBOL_CODE_MAPPING.put("lightsnow", 49);
        SYMBOL_CODE_MAPPING.put("heavysnow", 50);
    }

    /* Thanks Chronus(app) */
    private static final int[] arrayWeatherIconToCode = {-1, /*1*/ 32, /*2*/ 34, /*3*/ 30, /*4*/ 26, /*5*/ 40, /*6*/ 39, /*7*/ 6, /*8*/ 14, /*9*/ 11, /*10*/ 12, /*11*/ 4, /*12*/ 18, /*13*/ 16, /*14*/ 15, /*15*/ 20, /*16*/ -1, /*17*/ -1, /*18*/ -1, /*19*/ -1, /*20*/ 42, /*21*/ 42, /*22*/ 4, /*23*/ 6, /*24*/ 39, /*25*/ 39, /*26*/ 42, /*27*/ 42, /*28*/ 42, /*29*/ 42, /*30*/ 4, /*31*/ 6, /*32*/ 6, /*33*/ 15, /*34*/ 15, /*35*/ -1, /*36*/ -1, /*37*/ -1, /*38*/ -1, /*39*/ -1, /*40*/ 40, /*41*/ 40, /*42*/ 6, /*43*/ 6, /*44*/ 14, /*45*/ 14, /*46*/ 9, /*47*/ 18, /*48*/ 18, /*49*/ 16, /*50*/ 16};

    private static int getPriorityCondition(String condition) {
        int endIndex = condition.indexOf("_");
        if(endIndex != -1) {
            condition = condition.substring(0, endIndex);
        }
        return SYMBOL_CODE_MAPPING.getOrDefault(condition, 0);
    }

    private void initTimeZoneFormat() {
        mGmt0Format.setTimeZone(TimeZone.getTimeZone("GMT"));
        mUserTimeZoneFormat.setTimeZone(TimeZone.getDefault());
    }

    private String convertTimeZone(String tmp) {
        try {
            return mUserTimeZoneFormat.format(mGmt0Format.parse(tmp));
        } catch (ParseException e) {
            return tmp;
        }
    }


    private String getDay(int i) {
        Calendar calendar = Calendar.getInstance();
        if(i > 0) {
            calendar.add(Calendar.DATE, i);
        }
        return mDayFormat.format(calendar.getTime());
    }

    private Boolean isMorningOrAfternoon(String time, boolean hasOneHour) {
        int endI = hasOneHour ? 17 : 13;
        for (int i = 6; i <= endI; i++) {
            if(time.contains((i < 10) ? "T0":"T" + i)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEndDay(String time) {
        for (int i = 18; i <= 23; i++) {
            if(time.contains("T" + i)) {
                return true;
            }
        }
        return false;
    }

    private String getNameLocality(String coordinate) {
        try {
            int separator = coordinate.indexOf('&');
            if (!coordinate.startsWith("lat=") || separator <= 4
                    || !coordinate.startsWith("lon=", separator + 1)) {
                return null;
            }
            double latitude = Double.parseDouble(coordinate.substring(4, separator));
            double longitude = Double.parseDouble(coordinate.substring(separator + 5));
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                return null;
            }
            String language = Locale.getDefault().getLanguage().replaceFirst("_", "-");
            String response = retrieve(String.format(Locale.US, URL_REVERSE,
                    Uri.encode(language), latitude, longitude));
            if (response == null) {
                return null;
            }
            JSONObject result = new JSONObject(response);
            JSONObject address = result.optJSONObject("address");
            String city = firstNonEmpty(address, "city", "town", "village", "municipality",
                    "suburb", "county");
            return TextUtils.isEmpty(city) ? result.optString("display_name", "") : city;
        } catch (JSONException | NumberFormatException e) {
            Log.w(TAG, "Received malformed reverse-location data");
        }
        return null;
    }

    private static float convertTemperature(double value, boolean metric) {
        if (!metric) {
            value = (value * 1.8) + 32;
        }
        return (float) value;
    }

    public boolean shouldRetry() {
        return false;
    }
}
