package com.gathin.tvhome;

/** The greeting above the clock: "Selamat pagi, Cipta Family". Pure logic, no Android. */
final class Greeting {
    private Greeting() {
    }

    /** pagi 05-10, siang 11-14, petang 15-17, malam 18-04 (hour is 0-23). */
    static String period(int hour) {
        int h = ((hour % 24) + 24) % 24;
        if (h >= 5 && h < 11) return "pagi";
        if (h >= 11 && h < 15) return "siang";
        if (h >= 15 && h < 18) return "petang";
        return "malam";
    }

    /** "Selamat petang, Cipta Family"; just "Selamat petang" when the name is empty. */
    static String text(int hour, String name) {
        String n = name == null ? "" : name.trim();
        return "Selamat " + period(hour) + (n.isEmpty() ? "" : ", " + n);
    }
}
