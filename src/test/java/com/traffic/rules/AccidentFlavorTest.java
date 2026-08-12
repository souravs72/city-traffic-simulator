package com.traffic.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Seeded caption determinism. User: implement research-grade plan. */
class AccidentFlavorTest {

    @Test
    void captionForSeed_isDeterministic() {
        assertEquals(AccidentFlavor.captionForSeed(42L), AccidentFlavor.captionForSeed(42L));
    }
}
