package com.traffic.rules;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Playful accident captions for demos and future UI banners. */
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
        int i = ThreadLocalRandom.current().nextInt(CAPTIONS.size());
        return CAPTIONS.get(i);
    }

    public static List<String> allCaptions() {
        return CAPTIONS;
    }
}
