package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Prayer times for the zone a theme names ("prayer_zone"): a JAKIM e-Solat zone ("TRG04"), or "ID" for
 * Indonesia, which asks AlAdhan for the Kemenag RI method at the theme's latitude/longitude.
 * A year of days is fetched once and kept on disk, so the row under the clock needs the network only
 * when the cache runs out. Every failure leaves the row as it was; a failed fetch is tried again an
 * hour later.
 */
final class PrayerTimes {
    private static final String TAG = "TvHome.Solat";
    private static final long RETRY_MS = 60L * 60 * 1000;
    /** JAKIM's keys, in the order the row shows them. */
    private static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] NAMES = {"Subuh", "Zuhur", "Asar", "Magrib", "Isya"};
    /** Month abbreviations as e-Solat writes them in its dates ("21-Okt-2026"). */
    private static final String[] MONTHS = {"jan", "feb", "mac", "apr", "mei", "jun", "jul", "ogo", "sep", "okt", "nov", "dis"};
    private static final String[] HIJRI_MONTHS = {"Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jumadilawal",
            "Jumadilakhir", "Rajab", "Syakban", "Ramadan", "Syawal", "Zulkaidah", "Zulhijah"};

    /** Zone name that selects AlAdhan (Kemenag RI method) for the theme's latitude and longitude. */
    static final String ID_ZONE = "ID";
    private static final String ID_TIMEZONE = "Asia/Jakarta";
    /** AlAdhan's calculation method 20 is Kementerian Agama Republik Indonesia. */
    private static final int ALADHAN_METHOD = 20;
    /** Minutes added per time, in AlAdhan's order: Imsak,Fajr,Sunrise,Dhuhr,Asr,Maghrib,Sunset,Isha,Midnight. */
    private static final String ALADHAN_TUNE = "0,0,0,0,0,0,0,0,0";
    /** Days to shift AlAdhan's Hijri date so it matches Kemenag's calendar (-1, 0 or 1). */
    private static final int HIJRI_ADJUST = 0;

    private static volatile double[] coords;

    /** One day's times, in minutes after midnight, and its Hijri date ("10 Jamadilawal 1448"). */
    static final class Day {
        final int[] minutes;
        final String hijri;

        Day(int[] minutes, String hijri) {
            this.minutes = minutes;
            this.hijri = hijri;
        }
    }

    private PrayerTimes() {
    }

    private static File file(Context ctx, String zone) {
        File base = Config.dir(ctx);
        if (base == null) return null;
        if (!ID_ZONE.equals(zone)) return new File(base, "solat-" + zone + ".json");
        double[] c = coords(ctx);
        return new File(base, String.format(Locale.ROOT, "solat-ID-%.2f_%.2f.json", c[0], c[1]));
    }

    /** The day of the cached year whose date is {@code key} ("21-10-2026"), or null. */
    static Day day(Context ctx, String zone, String key) {
        JSONArray days = read(file(ctx, zone));
        for (int i = 0; days != null && i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d == null || !key.equals(dateKey(d.optString("date")))) continue;
            int[] m = new int[KEYS.length];
            for (int k = 0; k < KEYS.length; k++) {
                m[k] = minutes(d.optString(KEYS[k]));
                if (m[k] < 0) return null;
            }
            return new Day(m, hijri(d.optString("hijri")));
        }
        return null;
    }

    /** True when the cache has no entry for today and no fetch failed in the last hour. */
    static boolean due(Context ctx, String zone, String todayKey) {
        if (day(ctx, zone, todayKey) != null) return false;
        return System.currentTimeMillis() - prefs(ctx).getLong("tried_" + zone, 0) >= RETRY_MS;
    }

    /** Downloads the year's times for the zone. Blocking: call from a background thread. */
    static boolean refresh(Context ctx, String zone) {
        prefs(ctx).edit().putLong("tried_" + zone, System.currentTimeMillis()).apply();
        File out = file(ctx, zone);
        if (out == null) return false;
        try {
            JSONArray days = ID_ZONE.equals(zone) ? fetchAladhan(ctx) : fetchJakim(zone);
            if (days == null || days.length() == 0) throw new IllegalStateException("no prayer times");
            File tmp = new File(out.getPath() + ".new");
            try (OutputStream os = new FileOutputStream(tmp)) {
                os.write(days.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(out)) throw new IllegalStateException("rename failed");
            Log.i(TAG, "saved " + days.length() + " days for " + zone);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "fetch failed for " + zone + ": " + e.getMessage());
            return false;
        }
    }

    private static JSONArray fetchJakim(String zone) throws Exception {
        byte[] body = get("https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=year&zone="
                + java.net.URLEncoder.encode(zone, "UTF-8"), 512 * 1024);
        return new JSONObject(new String(body, StandardCharsets.UTF_8)).optJSONArray("prayerTime");
    }

    /** Twelve months from this one, a request per month; all or nothing, so a gap never reaches the disk. */
    private static JSONArray fetchAladhan(Context ctx) throws Exception {
        double[] c = coords(ctx);
        Calendar now = Calendar.getInstance();
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH) + 1;
        JSONArray all = new JSONArray();
        for (int i = 0; i < 12; i++) {
            StringBuilder url = new StringBuilder(String.format(Locale.ROOT,
                    "https://api.aladhan.com/v1/calendar/%d/%d?latitude=%.4f&longitude=%.4f&method=%d"
                            + "&timezonestring=%s&tune=%s",
                    y, m, c[0], c[1], ALADHAN_METHOD, ID_TIMEZONE, ALADHAN_TUNE));
            if (HIJRI_ADJUST != 0) url.append("&adjustment=").append(HIJRI_ADJUST);
            appendAladhan(all, new String(get(url.toString(), 512 * 1024), StandardCharsets.UTF_8));
            if (++m > 12) {
                m = 1;
                y++;
            }
        }
        return all;
    }

    /**
     * Adds one AlAdhan calendar month ({"data":[{"timings":{"Fajr":"05:12 (WIB)",...},"date":{"gregorian":
     * {"date":"01-11-2026"},"hijri":{"day":"18","month":{"number":5},"year":"1448"}}}]}) in the shape
     * the cache and {@link #day} read: date, five times as HH:mm, and the Hijri date as "1448-5-18".
     */
    static void appendAladhan(JSONArray out, String json) throws org.json.JSONException {
        JSONArray data = new JSONObject(json).getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONObject d = data.getJSONObject(i);
            JSONObject t = d.getJSONObject("timings");
            JSONObject date = d.getJSONObject("date");
            JSONObject h = date.getJSONObject("hijri");
            JSONObject row = new JSONObject();
            row.put("date", date.getJSONObject("gregorian").getString("date"));
            row.put("fajr", clean(t.getString("Fajr")));
            row.put("dhuhr", clean(t.getString("Dhuhr")));
            row.put("asr", clean(t.getString("Asr")));
            row.put("maghrib", clean(t.getString("Maghrib")));
            row.put("isha", clean(t.getString("Isha")));
            row.put("hijri", h.getString("year") + "-" + h.getJSONObject("month").getInt("number") + "-" + h.getString("day"));
            out.put(row);
        }
    }

    /** "05:12 (WIB)" to "05:12". */
    static String clean(String time) {
        String s = time.trim();
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : s;
    }

    /** The theme's latitude and longitude, read once. */
    private static double[] coords(Context ctx) {
        double[] c = coords;
        if (c == null) {
            Config cfg = Config.load(ctx);
            c = new double[]{cfg.latitude, cfg.longitude};
            coords = c;
        }
        return c;
    }

    /** "21-10-2026" for a calendar day, the key {@link #dateKey} gives e-Solat's dates. */
    static String key(Calendar c) {
        return String.format(Locale.ROOT, "%02d-%02d-%04d", c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    /** "21-Okt-2026" (or "21-Oct-2026") to "21-10-2026"; "" when it is not a date. */
    static String dateKey(String date) {
        String[] p = date.trim().split("-");
        if (p.length != 3) return "";
        if (p[1].trim().matches("\\d{1,2}")) {
            try {
                return String.format(Locale.ROOT, "%02d-%02d-%04d", Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if (p[1].length() < 3) return "";
        String mon = p[1].substring(0, 3).toLowerCase(Locale.ROOT);
        int m = -1;
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(mon)) m = i;
        // English spellings where they differ from Malay.
        if (m < 0) m = "mar".equals(mon) ? 2 : "may".equals(mon) ? 4 : "aug".equals(mon) ? 7
                : "oct".equals(mon) ? 9 : "dec".equals(mon) ? 11 : -1;
        if (m < 0) return "";
        try {
            return String.format(Locale.ROOT, "%02d-%02d-%04d", Integer.parseInt(p[0]), m + 1, Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** "13:01:00" to 781; -1 when it is not a time. */
    static int minutes(String hms) {
        String[] p = hms.trim().split(":");
        if (p.length < 2) return -1;
        try {
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h < 0 || h > 23 || m < 0 || m > 59 ? -1 : h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "1448-05-10" to "10 Jamadilawal 1448"; "" when it is not a date. */
    static String hijri(String ymd) {
        String[] p = ymd.trim().split("-");
        if (p.length != 3) return "";
        try {
            int m = Integer.parseInt(p[1]);
            if (m < 1 || m > 12) return "";
            return Integer.parseInt(p[2]) + " " + HIJRI_MONTHS[m - 1] + " " + Integer.parseInt(p[0]);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** The index of the next prayer after {@code now} minutes, or -1 when Isyak has passed. */
    static int next(int[] minutes, int now) {
        for (int i = 0; i < minutes.length; i++) if (minutes[i] > now) return i;
        return -1;
    }

    /** "dalam 12 mnt", "dalam 1 jam 5 mnt", "dalam 2 jam". */
    static String countdown(int minutesLeft) {
        int h = minutesLeft / 60, m = minutesLeft % 60;
        if (h == 0) return "dalam " + Math.max(1, m) + " mnt";
        return m == 0 ? "dalam " + h + " jam" : "dalam " + h + " jam " + m + " mnt";
    }

    /** "05:49" in 24-hour time, "5:49" in 12-hour time (the clock beside it says AM or PM). */
    static String format(int minutes, boolean is24) {
        int h = minutes / 60, m = minutes % 60;
        if (is24) return String.format(Locale.ROOT, "%02d:%02d", h, m);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.ROOT, "%d:%02d", h12, m);
    }

    private static JSONArray read(File f) {
        if (f == null || !f.isFile() || f.length() > 512 * 1024) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new JSONArray(new String(buf, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] get(String url, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
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
        return ctx.getSharedPreferences("prayer_times", Context.MODE_PRIVATE);
    }
}
