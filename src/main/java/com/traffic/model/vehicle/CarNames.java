package com.traffic.model.vehicle;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Alphabetical car plates: A, B, … Z, then AA, AB, AC, …
 */
public final class CarNames {

    private CarNames() {
    }

    /** 0 → A, 25 → Z, 26 → AA, 27 → AB, … */
    public static String forIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        int n = index;
        StringBuilder sb = new StringBuilder();
        while (true) {
            sb.append((char) ('A' + (n % 26)));
            n = n / 26 - 1;
            if (n < 0) {
                break;
            }
        }
        return sb.reverse().toString();
    }

    public static String forId(int id) {
        return forIndex(Math.floorMod(id, Integer.MAX_VALUE));
    }

    /** Next free letter-code not already used by the fleet. */
    public static String pickUnused(Collection<String> taken) {
        Set<String> used = new HashSet<>();
        if (taken != null) {
            for (String name : taken) {
                if (name != null && !name.isBlank()) {
                    used.add(name.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        for (int i = 0; i < 100_000; i++) {
            String candidate = forIndex(i);
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return forIndex(taken == null ? 0 : taken.size());
    }
}
