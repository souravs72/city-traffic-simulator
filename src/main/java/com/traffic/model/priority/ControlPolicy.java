package com.traffic.model.priority;

/**
 * A/B control regimes for rigorous comparison.
 * <ul>
 *   <li>{@link #MAPS_LIKE} — congestion-aware routing only (Google-Maps-class). No
 *       emergency preemption, no VIP corridors, no priority departure order.</li>
 *   <li>{@link #CITY_FLOW} — full priority stack: FIRE &gt; AMBULANCE &gt; POLICE &gt; VIP &gt; CIVILIAN,
 *       signal preemption, corridor diversion, priority departures.</li>
 * </ul>
 */
public enum ControlPolicy {
    MAPS_LIKE,
    CITY_FLOW;

    public boolean honorPriority() {
        return this == CITY_FLOW;
    }
}
