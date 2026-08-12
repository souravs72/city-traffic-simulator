package com.traffic.api.dto;

/** Dispatch FIRE/AMBULANCE/POLICE from the nearest matching facility to a scene node. */
public record DispatchRequest(String serviceClass, int sceneNodeId) {
}
