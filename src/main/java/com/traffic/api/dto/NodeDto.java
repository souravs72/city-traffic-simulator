package com.traffic.api.dto;

/** Facility defaults to NONE when null (backward-compatible with older saves). */
public record NodeDto(int id, String label, double x, double y, String facility) {
    public NodeDto(int id, String label, double x, double y) {
        this(id, label, x, y, "NONE");
    }
}
