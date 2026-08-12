package com.traffic.api.dto;

public record ConnectEdgeRequest(int from, int to, int capacity, boolean twoWay) {
}
