package com.traffic.sim;

import com.traffic.model.graph.EdgeId;
import com.traffic.model.priority.CorridorBoard;
import com.traffic.model.priority.PriorityMechanisms;
import com.traffic.model.priority.VipLockdown;
import com.traffic.model.traffic.TrafficState;
import com.traffic.model.vehicle.ServiceClass;
import com.traffic.model.vehicle.Vehicle;
import com.traffic.model.vehicle.VehiclePosition;
import com.traffic.rules.Replanner;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** VIP corridor arming + civilian diversion around hard locks. */
public final class VipOps {

    private final CorridorBoard corridors;
    private final AtomicInteger corridorSeq;

    public VipOps(CorridorBoard corridors, AtomicInteger corridorSeq) {
        this.corridors = corridors;
        this.corridorSeq = corridorSeq;
    }

    public void armVipLockdown(
            TrafficState traffic,
            PriorityMechanisms mechanisms,
            Vehicle vip,
            int departAt,
            int worldTick,
            Runnable replanAround
    ) {
        List<EdgeId> edges = vip.remainingEdgesView();
        if (edges.isEmpty() || mechanisms == null || !mechanisms.corridorBlocking()) {
            return;
        }
        int lead = 3;
        int start = Math.max(0, departAt - lead);
        int end = departAt + Math.max(16, edges.size() * 5);
        VipLockdown.Plan plan = VipLockdown.plan(traffic.graph(), edges);
        corridors.activate(new CorridorBoard.Corridor(
                "vip-" + corridorSeq.incrementAndGet(),
                ServiceClass.VIP,
                plan.hardClosed(),
                plan.softBuffer(),
                start,
                end
        ));
        corridors.setCurrentTick(worldTick);
        replanAround.run();
    }

    public int replanAroundCorridors(
            PriorityMechanisms mechanisms,
            Replanner replanner,
            List<Vehicle> fleet,
            TrafficState traffic,
            CorridorBoard corridors
    ) {
        if (mechanisms == null || !mechanisms.corridorBlocking() || replanner == null) {
            return 0;
        }
        int n = 0;
        for (Vehicle vehicle : fleet) {
            if (vehicle.arrived() || vehicle.serviceClass().isEmergency()) {
                continue;
            }
            if (vehicle.serviceClass() == ServiceClass.VIP) {
                continue;
            }
            if (!(vehicle.position() instanceof VehiclePosition.AtNode)) {
                continue;
            }
            if (!corridors.pathBlocked(vehicle.remainingEdgesView(), vehicle.serviceClass())) {
                continue;
            }
            int before = vehicle.replanCount();
            if (replanner.replan(vehicle, traffic.graph()) && vehicle.replanCount() > before) {
                n++;
            }
        }
        return n;
    }
}
