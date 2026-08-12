package com.traffic.api.dto;

public record AddNodeRequest(double x, double y, String label, String facility) {
    public AddNodeRequest(double x, double y, String label) {
        this(x, y, label, "NONE");
    }
}
