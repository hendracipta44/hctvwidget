package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Open-Meteo's reading, as the line beside the greeting. */
public class WeatherTest {

    @Test
    public void readsTheCurrentReading() throws Exception {
        Weather.Now w = Weather.parse("{\"latitude\":0.9,\"current\":{\"time\":\"2026-11-01T10:00\","
                + "\"temperature_2m\":29.6,\"weather_code\":2}}");
        assertEquals(30, w.tempC);
        assertEquals(2, w.code);
        assertEquals("30\u00B0 Berawan sebagian", Weather.line(w));
    }

    @Test
    public void describesCodesInIndonesian() {
        assertEquals("Cerah", Weather.describe(0));
        assertEquals("Hujan lebat", Weather.describe(65));
        assertEquals("Badai petir", Weather.describe(95));
        assertEquals("", Weather.describe(1234));
        assertEquals("27\u00B0", Weather.line(new Weather.Now(27, 1234)));
    }
}
