package com.traffic.sim;

import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.FacilityKind;
import com.traffic.model.graph.Node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Places hospitals, police, fire, and VIP sites across a generated city. */
public final class FacilitySeeder {

    private FacilitySeeder() {
    }

    public static void seed(EditableCity city, long seed) {
        List<Node> nodes = new ArrayList<>(city.nodes());
        if (nodes.size() < 8) {
            return;
        }
        boolean any = nodes.stream().anyMatch(n -> n.facility() != FacilityKind.NONE);
        if (any) {
            return; // already designated
        }

        Random rng = new Random(seed ^ 0xFAC1117L);
        double cx = nodes.stream().mapToDouble(Node::x).average().orElse(0);
        double cy = nodes.stream().mapToDouble(Node::y).average().orElse(0);

        // Hospitals: one near center, one toward rim
        place(city, nodes, FacilityKind.HOSPITAL, cx, cy, 0);
        place(city, nodes, FacilityKind.HOSPITAL, cx + 280, cy - 160, 1);

        // Police: mid-city + rim
        place(city, nodes, FacilityKind.POLICE_STATION, cx - 200, cy + 120, 2);
        place(city, nodes, FacilityKind.POLICE_STATION, cx + 160, cy + 200, 3);

        // Fire stations: spaced
        place(city, nodes, FacilityKind.FIRE_STATION, cx - 120, cy - 200, 4);
        place(city, nodes, FacilityKind.FIRE_STATION, cx + 220, cy + 40, 5);

        // VIP site: prestigious centralish node
        place(city, nodes, FacilityKind.VIP_SITE, cx + 40, cy + 30, 6);

        // Extra hospital/police on larger maps
        if (nodes.size() > 80) {
            place(city, nodes, FacilityKind.HOSPITAL, cx - 260, cy - 80, 7);
            place(city, nodes, FacilityKind.POLICE_STATION, cx + 300, cy - 120, 8);
            place(city, nodes, FacilityKind.FIRE_STATION, cx - 40, cy + 260, 9);
        }

        // Nudge labels for readability without colliding uniqueness too hard
        for (Node n : city.nodes()) {
            if (n.facility() == FacilityKind.NONE) {
                continue;
            }
            String tag = switch (n.facility()) {
                case HOSPITAL -> "Hospital";
                case POLICE_STATION -> "Police";
                case FIRE_STATION -> "Fire";
                case VIP_SITE -> "VIP";
                default -> n.label();
            };
            if (!n.label().contains(tag)) {
                city.setFacility(n.id(), n.facility()); // keep facility; label stays unless we add rename API
            }
        }
        // silence unused
        rng.nextInt();
    }

    private static void place(
            EditableCity city,
            List<Node> nodes,
            FacilityKind kind,
            double tx,
            double ty,
            int salt
    ) {
        Node best = nodes.stream()
                .filter(n -> n.facility() == FacilityKind.NONE)
                .min(Comparator.comparingDouble(n ->
                        Math.hypot(n.x() - tx, n.y() - ty) + (n.id().value() % 7) * 0.01 * salt))
                .orElse(null);
        if (best != null) {
            city.setFacility(best.id(), kind);
        }
    }
}
