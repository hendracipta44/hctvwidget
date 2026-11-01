package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Current weather from Open-Meteo (free, no key) for the theme's latitude/longitude, kept in
 * SharedPreferences. Refreshed at most every 30 minutes; a failed fetch is tried again after 10, and
 * the line is hidden once the last good reading is over three hours old. Never throws to the caller.
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    private static final long REFRESH_MS = 30L * 60 * 1000;
    private static final long RETRY_MS = 10L * 60 * 1000;
    private static final long STALE_MS = 3L * 60 * 60 * 1000;

    /** One reading: whole degrees Celsius and the WMO weather code. */
    static final class Now {
        final int tempC;
        final int code;

        Now(int tempC, int code) {
            this.tempC = tempC;
            this.code = code;
        }
    }

    private Weather() {
    }

    /** The last good reading, or null when there is none or it is too old to show. */
    static Now recent(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long at = p.getLong("ok_at", 0);
        if (at == 0 || System.currentTimeMillis() - at > STALE_MS) return null;
        return new Now(p.getInt("temp", 0), p.getInt("code", -1));
    }

    /** True when the reading is over 30 minutes old and no fetch was tried in the last 10. */
    static boolean due(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        return now - p.getLong("ok_at", 0) >= REFRESH_MS && now - p.getLong("tried", 0) >= RETRY_MS;
    }

    /** Downloads the current weather. Blocking: call from a background thread. */
    static boolean refresh(Context ctx, double lat, double lng) {
        prefs(ctx).edit().putLong("tried", System.currentTimeMillis()).apply();
        try {
            String url = String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code&timezone=Asia%%2FJakarta", lat, lng);
            Now w = parse(new String(get(url, 64 * 1024), StandardCharsets.UTF_8));
            prefs(ctx).edit().putInt("temp", w.tempC).putInt("code", w.code)
                    .putLong("ok_at", System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "fetch failed: " + e.getMessage());
            return false;
        }
    }

    /** Reads Open-Meteo's {"current":{"temperature_2m":29.4,"weather_code":2}}. */
    static Now parse(String json) throws JSONException {
        JSONObject cur = new JSONObject(json).getJSONObject("current");
        return new Now((int) Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"));
    }

    /** The WMO weather code in Indonesian; "" for a code we do not know. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Cerah";
            case 1: return "Cerah berawan";
            case 2: return "Berawan sebagian";
            case 3: return "Mendung";
            case 45: case 48: return "Berkabut";
            case 51: case 53: case 55: case 56: case 57: return "Gerimis";
            case 61: case 63: case 66: case 67: case 80: case 81: return "Hujan";
            case 65: case 82: return "Hujan lebat";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Salju";
            case 95: case 96: case 99: return "Badai petir";
            default: return "";
        }
    }

    /** "29° Berawan sebagian". */
    static String line(Now w) {
        String d = describe(w.code);
        return w.tempC + "\u00B0" + (d.isEmpty() ? "" : " " + d);
    }

    private static byte[] get(String url, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > maxBytes) throw new IllegalStateException("response too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }
}
