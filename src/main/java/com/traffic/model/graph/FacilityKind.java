package com.traffic.model.graph;

/**
 * Designated city facilities. Used as spawn/destination anchors for priority services.
 */
public enum FacilityKind {
    NONE,
    HOSPITAL,
    POLICE_STATION,
    FIRE_STATION,
    VIP_SITE;

    public static FacilityKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return FacilityKind.valueOf(raw.trim().toUpperCase());
    }
}
