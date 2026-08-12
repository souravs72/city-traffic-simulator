package com.traffic.sim;

import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadGraph;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;

import java.util.Objects;
import java.util.function.Function;

/** Dispatch FIRE/AMBULANCE/POLICE from nearest matching facility. */
public final class EmergencyDispatch {

    @FunctionalInterface
    public interface TripAdder {
        Vehicle add(NodeId from, NodeId to, String name, ServiceClass sc, int depart);
    }

    private EmergencyDispatch() {
    }

    public static Vehicle dispatch(
            EditableCity city,
            RoadGraph graph,
            ServiceClass serviceClass,
            NodeId scene,
            boolean hasUnappliedEdits,
            Runnable applyEdits,
            TripAdder addTrip
    ) {
        Objects.requireNonNull(serviceClass, "serviceClass");
        Objects.requireNonNull(scene, "scene");
        if (!serviceClass.isEmergency()) {
            throw new IllegalArgumentException("dispatch requires FIRE, AMBULANCE, or POLICE");
        }
        if (hasUnappliedEdits) {
            applyEdits.run();
        }
        FacilityKind want = switch (serviceClass) {
            case FIRE -> FacilityKind.FIRE_STATION;
            case AMBULANCE -> FacilityKind.HOSPITAL;
            case POLICE -> FacilityKind.POLICE_STATION;
            default -> FacilityKind.NONE;
        };
        Node origin = FacilityLocator.nearest(city, graph, want, scene);
        if (origin == null) {
            throw new IllegalStateException("No " + want.name() + " facility on the map — place one first");
        }
        return addTrip.add(origin.id(), scene, null, serviceClass, 0);
    }
}
