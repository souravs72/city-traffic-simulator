package com.traffic.model.signal;

/**
 * Traffic light color.
 * <ul>
 *   <li>GREEN — new cars may enter</li>
 *   <li>YELLOW — clearing; no new entries (cars already on the road keep going)</li>
 *   <li>RED — stop; no new entries</li>
 * </ul>
 */
public enum LightColor {
    GREEN,
    YELLOW,
    RED;

    /** Realistic rule: only solid green starts a new crossing. */
    public boolean allowsNewEntry() {
        return this == GREEN;
    }
}
