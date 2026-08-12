package com.traffic.api;

import com.traffic.api.dto.AccidentRequest;
import com.traffic.api.dto.ConnectEdgeRequest;
import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.api.dto.SessionSnapshotDto;
import com.traffic.config.CityGenConfig;
import com.traffic.config.SimConfig;
import com.traffic.model.graph.EdgeId;
import com.traffic.model.graph.NodeId;
import com.traffic.model.signal.LightTiming;
import com.traffic.routing.RoutingAlgorithm;
import com.traffic.rules.AccidentFlavor;
import com.traffic.sim.CitySession;
import com.traffic.sim.SessionMode;

/** Single in-memory session for the React UI (v1). */
public final class SessionHub {

    private CitySession session;

    public synchronized SessionSnapshotDto create(CreateSessionRequest req) {
        int rows = req.rows() > 0 ? req.rows() : 8;
        int cols = req.cols() > 0 ? req.cols() : 8;
        int fleet = req.fleetSize() > 0 ? req.fleetSize() : 8;
        int fuel = req.initialFuel() > 0 ? req.initialFuel() : 200;
        int maxTicks = req.maxTicks() > 0 ? req.maxTicks() : 300;
        long seed = req.seed();

        SimConfig config = new SimConfig(
                maxTicks,
                fuel,
                RoutingAlgorithm.DIJKSTRA,
                LightTiming.playful(),
                2,
                8,
                false
        );
        CityGenConfig gen = new CityGenConfig(rows, cols, 3.0, 3, true);
        this.session = CitySession.openGrid(config, gen, fleet, seed);
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto snapshot() {
        return SnapshotMapper.from(requireSession());
    }

    public synchronized SessionSnapshotDto build() {
        requireSession().build();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto play() {
        requireSession().play();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto apply() {
        requireSession().applyEdits();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto step() {
        requireSession().step();
        return SnapshotMapper.from(session);
    }

    public synchronized SessionSnapshotDto run(int ticks) {
        CitySession s = requireSession();
        int n = ticks > 0 ? ticks : 10;
        s.run(n);
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto connect(ConnectEdgeRequest req) {
        CitySession s = requireSession();
        if (s.mode() != SessionMode.BUILD) {
            throw new IllegalStateException("Connect edges only in BUILD mode");
        }
        int capacity = req.capacity() > 0 ? req.capacity() : 2;
        NodeId from = new NodeId(req.from());
        NodeId to = new NodeId(req.to());
        if (req.twoWay()) {
            s.city().connectTwoWay(from, to, capacity);
        } else {
            s.city().connectOneWay(from, to, capacity);
        }
        return SnapshotMapper.from(s);
    }

    public synchronized SessionSnapshotDto accident(AccidentRequest req) {
        CitySession s = requireSession();
        String caption = req.caption() == null || req.caption().isBlank()
                ? AccidentFlavor.randomCaption()
                : req.caption();
        int duration = req.durationTicks() > 0 ? req.durationTicks() : s.config().accidentDurationTicks();
        s.traffic().reportAccident(new EdgeId(req.edgeId()), duration, caption);
        return SnapshotMapper.from(s);
    }

    private CitySession requireSession() {
        if (session == null) {
            throw new IllegalStateException("No session — POST /api/session first");
        }
        return session;
    }
}
