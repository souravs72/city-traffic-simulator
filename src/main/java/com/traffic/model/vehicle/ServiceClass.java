package com.traffic.model.vehicle;

/**
 * Absolute priority ladder for CityFlow control.
 * FIRE > AMBULANCE > POLICE > VIP > CIVILIAN.
 * Matches real-world emergency precedence; VIP sits below all three emergency arms.
 */
public enum ServiceClass {
    CIVILIAN(0, false),
    VIP(1, false),
    POLICE(2, true),
    AMBULANCE(3, true),
    FIRE(4, true);

    private final int rank;
    private final boolean emergency;

    ServiceClass(int rank, boolean emergency) {
        this.rank = rank;
        this.emergency = emergency;
    }

    public int rank() {
        return rank;
    }

    public boolean isEmergency() {
        return emergency;
    }

    public boolean outranks(ServiceClass other) {
        return rank > other.rank;
    }

    /** Classic emergency arms (fire/ambulance/police). */
    public boolean preemptsSignals() {
        return emergency;
    }

    /**
     * VIP + emergency get signal privilege: they only stop when a higher-rank
     * unit needs the conflicting approach.
     */
    public boolean getsSignalPrivilege() {
        return rank >= VIP.rank;
    }

    public static ServiceClass parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CIVILIAN;
        }
        return ServiceClass.valueOf(raw.trim().toUpperCase());
    }

    public String displayName() {
        return switch (this) {
            case FIRE -> "Fire";
            case AMBULANCE -> "Ambulance";
            case POLICE -> "Police";
            case VIP -> "VIP";
            case CIVILIAN -> "Civilian";
        };
    }
}
