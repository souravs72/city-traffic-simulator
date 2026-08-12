package com.traffic;

import com.traffic.config.CityGenConfig;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.Node;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.rules.AccidentFlavor;
import com.traffic.sim.CitySession;
import com.traffic.sim.SessionMode;

/**
 * Grid-city demo: generate a downtown, doodle a shortcut in BUILD, then PLAY.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SimConfig config = new SimConfig(
                300,
                200,
                com.traffic.routing.RoutingAlgorithm.DIJKSTRA,
                com.traffic.model.signal.LightTiming.playful(),
                2,
                8,
                true,
                8
        );
        CitySession session = CitySession.openGrid(
                config,
                CityGenConfig.downtown(),
                12,
                42L
        );

        System.out.println("=== City Traffic Simulator — grid session ===");
        System.out.println("mode=" + session.mode()
                + " | nodes=" + session.city().nodeCount()
                + " | edges=" + session.city().edgeCount()
                + " | cars=" + session.fleet().size());

        // BUILD: player draws a playful diagonal shortcut
        Node corner = session.city().nodes().stream()
                .filter(n -> n.label().equals("R0C0"))
                .findFirst()
                .orElseThrow();
        Node far = session.city().nodes().stream()
                .filter(n -> n.label().equals("R7C7"))
                .findFirst()
                .orElseThrow();
        session.city().connectOneWay(corner.id(), far.id(), 4);
        System.out.println("BUILD: drew shortcut " + corner.label() + " → " + far.label()
                + " (unapplied=" + session.hasUnappliedEdits() + ")");

        // Drop a ✕ on some busy-looking edge after we enter play (post-apply)
        session.play();
        System.out.println("PLAY: map version applied, mode=" + SessionMode.PLAY);

        var anyEdge = session.traffic().graph().edges().iterator().next();
        session.traffic().reportAccident(
                anyEdge.id(),
                config.accidentDurationTicks(),
                AccidentFlavor.randomCaption()
        );
        System.out.println("✕ " + session.traffic().activeAccidents().get(0).caption()
                + " on edge " + anyEdge.id().value());
        System.out.println();

        int steps = session.run(config.maxTicks());
        System.out.println();
        System.out.println("done: steps=" + steps
                + " arrived=" + session.arrivedCount() + "/" + session.fleet().size()
                + " worldTick=" + session.worldTick());
        for (Vehicle car : session.fleet()) {
            System.out.println("  car#" + car.id().value()
                    + " arrived=" + car.arrived()
                    + " replans=" + car.replanCount()
                    + " fuelLeft=" + car.fuel());
        }
    }
}
