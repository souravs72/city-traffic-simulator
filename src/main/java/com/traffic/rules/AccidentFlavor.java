package com.traffic.rules;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Accident captions. Callers: SessionHub accident handlers.
 * User instruction: implement research-grade CityFlow plan (seeded captions).
 */
public final class AccidentFlavor {

    private static final List<String> CAPTIONS = List.of(
            "Banana peel pileup",
            "Duck crossing committee",
            "Coffee spill standoff",
            "Mime traffic jam",
            "Lost pizza delivery spiral",
            "Tourist map argument",
            "Runaway shopping cart derby",
            "Street musician encore blockade"
    );

    private AccidentFlavor() {
    }

    public static String randomCaption() {
        return captionAt(ThreadLocalRandom.current().nextInt(CAPTIONS.size()));
    }

    /** Seeded caption for reproducible eval / scenario dumps. */
    public static String captionForSeed(long seed) {
        Random rng = new Random(seed);
        return captionAt(rng.nextInt(CAPTIONS.size()));
    }

    public static String captionAt(int index) {
        int i = Math.floorMod(index, CAPTIONS.size());
        return CAPTIONS.get(i);
    }

    public static List<String> allCaptions() {
        return CAPTIONS;
    }
}
