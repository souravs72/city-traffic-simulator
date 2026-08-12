package com.traffic.model.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CarNamesTest {

    @Test
    void alphabeticalThenDoubleLetters() {
        assertEquals("A", CarNames.forIndex(0));
        assertEquals("B", CarNames.forIndex(1));
        assertEquals("Z", CarNames.forIndex(25));
        assertEquals("AA", CarNames.forIndex(26));
        assertEquals("AB", CarNames.forIndex(27));
    }

    @Test
    void pickUnusedSkipsTaken() {
        assertEquals("C", CarNames.pickUnused(List.of("A", "B")));
        assertEquals("AA", CarNames.pickUnused(Set.of(
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
        )));
    }
}
