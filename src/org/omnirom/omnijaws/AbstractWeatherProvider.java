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

import static java.net.HttpURLConnection.HTTP_OK;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import android.content.Context;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

public abstract class AbstractWeatherProvider {
    private static final String TAG = "AbstractWeatherProvider";
    private static final boolean DEBUG = false;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final String MET_NO_IP = "157.249.81.141";
    private static final String NOMINATIM_IP = "151.101.129.91";
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "api.met.no",
            "api.openweathermap.org",
            "geocoding-api.open-meteo.com",
            "nominatim.openstreetmap.org"));
    private static final String USER_AGENT = "exTHmUI/12 OmniJaws "
            + "(https://github.com/exthmui12-polaris-unofficial/"
            + "android_packages_services_OmniJaws)";
    protected Context mContext;

    public AbstractWeatherProvider(Context context) {
        mContext = context;
    }

    protected String retrieve(String url) {
        return retrieve(url, 0, null);
    }

    private String retrieve(String rawUrl, int redirectCount, String originalHost) {
        if (!isSafeHttpsUrl(rawUrl) || redirectCount > MAX_REDIRECTS) {
            Log.w(TAG, "Rejected non-HTTPS or invalid weather endpoint");
            return null;
        }
        HttpsURLConnection request = null;
        try {
            URL endpoint = new URL(rawUrl);
            String endpointHost = endpoint.getHost().toLowerCase(Locale.US);
            if (originalHost != null && !originalHost.equals(endpointHost)) {
                Log.w(TAG, "Rejected cross-host weather redirect");
                return null;
            }
            request = (HttpsURLConnection) endpoint.openConnection();
            if ("api.met.no".equals(endpointHost)) {
                request.setSSLSocketFactory(new FixedAddressSSLSocketFactory(
                        (SSLSocketFactory) SSLSocketFactory.getDefault(), MET_NO_IP, endpointHost));
            } else if ("nominatim.openstreetmap.org".equals(endpointHost)) {
                request.setSSLSocketFactory(new FixedAddressSSLSocketFactory(
                        (SSLSocketFactory) SSLSocketFactory.getDefault(), NOMINATIM_IP, endpointHost));
            }
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(CONNECT_TIMEOUT_MS);
            request.setReadTimeout(READ_TIMEOUT_MS);
            request.setRequestProperty("Accept", "application/json");
            request.setRequestProperty("User-Agent", USER_AGENT);
            int code = request.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = request.getHeaderField("Location");
                if (TextUtils.isEmpty(location)) {
                    return null;
                }
                URI redirected = endpoint.toURI().resolve(location);
                return retrieve(redirected.toString(), redirectCount + 1, endpointHost);
            }
            if (code != HTTP_OK) {
                log(TAG, "Weather endpoint returned HTTP " + code);
                return null;
            }
            BufferedReader response = new BufferedReader(
                    new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8));
            String inputLine;
            StringBuilder entity = new StringBuilder();
            int bytes = 0;
            while ((inputLine = response.readLine()) != null) {
                bytes += inputLine.length();
                if (bytes > MAX_RESPONSE_BYTES) {
                    Log.w(TAG, "Weather response exceeded size limit");
                    return null;
                }
                entity.append(inputLine);
            }
            response.close();
            return entity.toString();
        } catch (MalformedURLException m) {
            Log.e(TAG, "Malformed weather endpoint");
        } catch (IOException | java.net.URISyntaxException e) {
            Log.w(TAG, "Weather request failed: " + e.getClass().getSimpleName());
        } finally {
            if (request != null) {
                request.disconnect();
            }
        }
        return null;
    }

    private static boolean isSafeHttpsUrl(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) {
            return false;
        }
        for (int i = 0; i < rawUrl.length(); i++) {
            char c = rawUrl.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        try {
            URI uri = new URI(rawUrl);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && !TextUtils.isEmpty(host)
                    && ALLOWED_HOSTS.contains(host.toLowerCase(Locale.US))
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    public abstract WeatherInfo getCustomWeather(String id, boolean metric);

    public abstract WeatherInfo getLocationWeather(Location location, boolean metric);

    public abstract List<WeatherInfo.WeatherLocation> getLocations(String input);

    public abstract boolean shouldRetry();

    protected void log(String tag, String msg) {
        // Do not emit URLs, API keys, coordinates, or provider response bodies. Keep this
        // hook for local debugging without making sensitive request data part of logcat.
        if (DEBUG) Log.d("WeatherService:" + tag, "provider event");
    }

    private static final class FixedAddressSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String address;
        private final String hostName;

        FixedAddressSSLSocketFactory(SSLSocketFactory delegate, String address, String hostName) {
            this.delegate = delegate;
            this.address = address;
            this.hostName = hostName;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            return delegate.createSocket(raw, host, port, true);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            if (autoClose) socket.close();
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            return delegate.createSocket(raw, hostName, port, true);
        }

        @Override
        public Socket createSocket(String host, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
            return createSocket(host, port);
        }

        @Override
        public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
            return createSocket(hostName, port);
        }

        @Override
        public Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
            return createSocket(hostName, port);
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }
    }
}
