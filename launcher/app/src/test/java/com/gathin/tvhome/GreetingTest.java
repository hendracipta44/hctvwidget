package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The greeting above the clock, by the hour. */
public class GreetingTest {

    @Test
    public void picksThePartOfTheDay() {
        assertEquals("pagi", Greeting.period(5));
        assertEquals("pagi", Greeting.period(10));
        assertEquals("siang", Greeting.period(11));
        assertEquals("siang", Greeting.period(14));
        assertEquals("petang", Greeting.period(15));
        assertEquals("petang", Greeting.period(17));
        assertEquals("malam", Greeting.period(18));
        assertEquals("malam", Greeting.period(0));
        assertEquals("malam", Greeting.period(4));
    }

    @Test
    public void addsTheName() {
        assertEquals("Selamat petang, Cipta Family", Greeting.text(16, "Cipta Family"));
        assertEquals("Selamat pagi", Greeting.text(7, ""));
        assertEquals("Selamat malam", Greeting.text(22, null));
    }
}
