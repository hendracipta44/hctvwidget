package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** e-Solat's and AlAdhan's dates and times, read into what the row under the clock shows. */
public class PrayerTimesTest {

    @Test
    public void readsMalayAndEnglishDates() {
        assertEquals("21-10-2026", PrayerTimes.dateKey("21-Okt-2026"));
        assertEquals("01-08-2026", PrayerTimes.dateKey("01-Ogos-2026"));
        assertEquals("05-03-2026", PrayerTimes.dateKey("05-Mac-2026"));
        assertEquals("31-12-2026", PrayerTimes.dateKey("31-Dec-2026"));
        assertEquals("", PrayerTimes.dateKey("not a date"));
    }

    @Test
    public void readsTimesAndHijri() {
        assertEquals(781, PrayerTimes.minutes("13:01:00"));
        assertEquals(-1, PrayerTimes.minutes("25:00:00"));
        assertEquals("10 Jumadilawal 1448", PrayerTimes.hijri("1448-05-10"));
        assertEquals("1 Ramadan 1448", PrayerTimes.hijri("1448-09-01"));
    }

    @Test
    public void findsTheNextPrayerAndCountsDown() {
        int[] day = {349, 781, 978, 1140, 1210};
        assertEquals(0, PrayerTimes.next(day, 300));
        assertEquals(2, PrayerTimes.next(day, 781));
        assertEquals(-1, PrayerTimes.next(day, 1215));
        assertEquals("dalam 12 mnt", PrayerTimes.countdown(12));
        assertEquals("dalam 1 jam 5 mnt", PrayerTimes.countdown(65));
        assertEquals("dalam 2 jam", PrayerTimes.countdown(120));
        assertEquals("05:49", PrayerTimes.format(349, true));
        assertEquals("1:01", PrayerTimes.format(781, false));
    }

    @Test
    public void readsNumericDatesFromAladhan() {
        assertEquals("01-11-2026", PrayerTimes.dateKey("01-11-2026"));
        assertEquals("05-03-2026", PrayerTimes.dateKey("5-3-2026"));
        assertEquals("", PrayerTimes.dateKey("01-xx-2026"));
    }

    @Test
    public void convertsAladhanCalendarMonth() throws Exception {
        String json = "{\"code\":200,\"data\":[{\"timings\":{\"Fajr\":\"05:02 (WIB)\",\"Sunrise\":\"06:10 (WIB)\","
                + "\"Dhuhr\":\"12:03 (WIB)\",\"Asr\":\"15:20 (WIB)\",\"Maghrib\":\"18:06 (WIB)\",\"Isha\":\"19:14 (WIB)\"},"
                + "\"date\":{\"gregorian\":{\"date\":\"01-11-2026\"},\"hijri\":{\"day\":\"20\","
                + "\"month\":{\"number\":5},\"year\":\"1448\"}}}]}";
        org.json.JSONArray out = new org.json.JSONArray();
        PrayerTimes.appendAladhan(out, json);
        org.json.JSONObject d = out.getJSONObject(0);
        assertEquals("01-11-2026", PrayerTimes.dateKey(d.getString("date")));
        assertEquals(302, PrayerTimes.minutes(d.getString("fajr")));
        assertEquals(723, PrayerTimes.minutes(d.getString("dhuhr")));
        assertEquals(920, PrayerTimes.minutes(d.getString("asr")));
        assertEquals(1086, PrayerTimes.minutes(d.getString("maghrib")));
        assertEquals(1154, PrayerTimes.minutes(d.getString("isha")));
        assertEquals("20 Jumadilawal 1448", PrayerTimes.hijri(d.getString("hijri")));
    }
}
