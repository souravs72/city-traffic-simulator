package com.traffic.api.dto;

/** VIP motorcade with police escorts and a timed corridor lockdown. */
public record VipConvoyRequest(int from, int to, int departAtTick, int escorts) {
}
