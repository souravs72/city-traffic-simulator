package com.traffic.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traffic.model.priority.ControlPolicy;
import com.traffic.model.priority.PriorityMechanisms;

import org.junit.jupiter.api.Test;

/** Mechanism factory tests. User: implement research-grade plan. */
class PriorityMechanismsTest {

    @Test
    void fromPolicy_mapsLikeIsNone_cityFlowIsFull() {
        assertEquals(PriorityMechanisms.none(), PriorityMechanisms.from(ControlPolicy.MAPS_LIKE));
        assertEquals(PriorityMechanisms.full(), PriorityMechanisms.from(ControlPolicy.CITY_FLOW));
    }

    @Test
    void ablationFactories_areExclusiveSingles() {
        assertTrue(PriorityMechanisms.departureOnly().priorityDeparture());
        assertFalse(PriorityMechanisms.departureOnly().signalPreemption());
        assertTrue(PriorityMechanisms.signalOnly().signalPreemption());
        assertTrue(PriorityMechanisms.corridorOnly().corridorBlocking());
        assertTrue(PriorityMechanisms.softRoutingOnly().softBufferRouting());
    }

    @Test
    void leaveOneOut_dropsExactlyOneFlag() {
        assertFalse(PriorityMechanisms.withoutCorridor().corridorBlocking());
        assertTrue(PriorityMechanisms.withoutCorridor().signalPreemption());
        assertEquals("FULL_MINUS_CORRIDOR", PriorityMechanisms.withoutCorridor().profileName());
        assertEquals("FULL", PriorityMechanisms.full().profileName());
        assertEquals("NONE", PriorityMechanisms.none().profileName());
    }

    @Test
    void mechanismProfile_matchesFactories() {
        assertEquals(PriorityMechanisms.full(), MechanismProfile.FULL.mechanisms());
        assertEquals(PriorityMechanisms.none(), MechanismProfile.NONE.mechanisms());
        assertEquals(PriorityMechanisms.withoutSignal(), MechanismProfile.FULL_MINUS_SIGNAL.mechanisms());
    }
}
