package com.traffic.sim;

import com.traffic.model.graph.Edge;
import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadType;

import java.util.ArrayList;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dense, irregular megacity with distinct districts — river + bridges, CBD,
 * old bazaar alleys, industrial fringe, suburban rim, and park voids.
 */
public final class OrganicCityGenerator {

    private static final double MIN_X = 60;
    private static final double MIN_Y = 50;
    private static final double MAX_X = 1340;
    private static final double MAX_Y = 820;

    private enum District {
        CBD, OLD_TOWN, PORT, INDUSTRIAL, SUBURB, RIVERSIDE
    }

    private OrganicCityGenerator() {
    }

    public static EditableCity generateKolkata(long seed) {
        return generate(seed ^ 0x4B4F4C4BL, true);
    }

    public static EditableCity generate(long seed) {
        return generate(seed, false);
    }

    private static EditableCity generate(long seed, boolean kolkata) {
        Random rng = new Random(seed);
        EditableCity city = new EditableCity();
        List<Node> nodes = new ArrayList<>();
        Map<Long, Node> cellIndex = new HashMap<>();

        // Irregular block spacing (wards of uneven size).
        List<Double> xs = irregularAxes(MIN_X, MAX_X, 15, 20, rng);
        List<Double> ys = irregularAxes(MIN_Y, MAX_Y, 12, 16, rng);

        double cx = (MIN_X + MAX_X) / 2;
        double cy = (MIN_Y + MAX_Y) / 2;

        for (int r = 0; r < ys.size(); r++) {
            for (int c = 0; c < xs.size(); c++) {
                double x = xs.get(c) + (rng.nextDouble() - 0.5) * 26;
                double y = ys.get(r) + (rng.nextDouble() - 0.5) * 26;
                x = clamp(x, MIN_X, MAX_X);
                y = clamp(y, MIN_Y, MAX_Y);
                if (inRiver(x, y)) {
                    continue;
                }
                // Parks / maidan voids — more breathing room in the fabric.
                if (inPark(x, y, rng)) {
                    continue;
                }
                District d = districtOf(x, y, cx, cy);
                // Drop density varies by district.
                double drop = switch (d) {
                    case CBD -> 0.03;
                    case OLD_TOWN -> 0.05;
                    case PORT, RIVERSIDE -> 0.07;
                    case INDUSTRIAL -> 0.06;
                    case SUBURB -> 0.14;
                };
                if (rng.nextDouble() < drop) {
                    continue;
                }
                String label = districtLabel(d, r, c, kolkata);
                Node node = city.addIntersection(x, y, label);
                nodes.add(node);
                cellIndex.put(pack(r, c), node);
            }
        }

        // Orthogonal links — missing lanes + type mix by district.
        for (int r = 0; r < ys.size(); r++) {
            for (int c = 0; c < xs.size(); c++) {
                Node here = cellIndex.get(pack(r, c));
                if (here == null) {
                    continue;
                }
                tryLink(city, here, cellIndex.get(pack(r, c + 1)), rng, false);
                tryLink(city, here, cellIndex.get(pack(r + 1, c)), rng, false);
            }
        }

        // Old-town bazaar diagonals (tight alleys).
        int diagonals = Math.max(18, nodes.size() / 14);
        for (int i = 0; i < diagonals; i++) {
            Node a = nodes.get(rng.nextInt(nodes.size()));
            if (districtOf(a.x(), a.y(), cx, cy) != District.OLD_TOWN
                    && districtOf(a.x(), a.y(), cx, cy) != District.CBD
                    && rng.nextDouble() < 0.45) {
                continue;
            }
            Node b = nearestUnlinked(city, a, nodes, rng, 160);
            if (b != null) {
                link(city, a.id(), b.id(), RoadType.ALLEY, true);
            }
        }

        // Major arterials + ring road.
        addArterialRibbons(city, cellIndex, ys.size(), xs.size(), rng);
        addRingRoad(city, nodes, cx, cy, rng);

        // Bridges across the river gap.
        addRiverBridges(city, nodes, rng);

        // Dense CBD / industrial densification.
        densifyCore(city, nodes, rng);
        densifyIndustrial(city, nodes, rng);

        // Waterfront spur along river banks.
        addWaterfront(city, nodes, rng);

        // Parks/river/missed links can leave islands — stitch until one fabric.
        stitchComponents(city);

        city.drainChanges();
        return city;
    }

    private static District districtOf(double x, double y, double cx, double cy) {
        double dx = x - cx;
        double dy = y - cy;
        double dist = Math.hypot(dx, dy);
        if (dist < 160) {
            return District.CBD;
        }
        if (Math.abs(x - riverX(y)) < 90) {
            return District.RIVERSIDE;
        }
        if (x < cx - 180 && y > cy) {
            return District.OLD_TOWN;
        }
        if (x > cx + 200 && y < cy + 40) {
            return District.PORT;
        }
        if (x > cx && y > cy + 80) {
            return District.INDUSTRIAL;
        }
        if (dist > 320) {
            return District.SUBURB;
        }
        return District.OLD_TOWN;
    }

    private static String districtLabel(District d, int r, int c, boolean kolkata) {
        if (kolkata) {
            String prefix = switch (d) {
                case CBD -> "Dalhousie";
                case OLD_TOWN -> "Burrabazar";
                case PORT -> "Strand";
                case INDUSTRIAL -> "Howrah";
                case SUBURB -> "SaltLake";
                case RIVERSIDE -> "Hooghly";
            };
            return prefix + "-" + r + "-" + c;
        }
        String prefix = switch (d) {
            case CBD -> "CBD";
            case OLD_TOWN -> "Bazaar";
            case PORT -> "Port";
            case INDUSTRIAL -> "Yard";
            case SUBURB -> "Suburb";
            case RIVERSIDE -> "Ghat";
        };
        return prefix + "-" + r + "-" + c;
    }

    private static boolean inPark(double x, double y, Random rng) {
        // Two maidans + a smaller green.
        if (Math.hypot(x - 520, y - 320) < 55) {
            return true;
        }
        if (Math.hypot(x - 980, y - 540) < 48) {
            return true;
        }
        return Math.hypot(x - 720, y - 700) < 36 && rng.nextDouble() < 0.85;
    }

    private static void densifyCore(EditableCity city, List<Node> nodes, Random rng) {
        double cx = (MIN_X + MAX_X) / 2;
        double cy = (MIN_Y + MAX_Y) / 2;
        List<Node> core = new ArrayList<>();
        for (Node n : nodes) {
            if (Math.hypot(n.x() - cx, n.y() - cy) < 200) {
                core.add(n);
            }
        }
        int extras = Math.min(36, Math.max(12, core.size() / 3));
        for (int i = 0; i < extras; i++) {
            if (core.isEmpty()) {
                break;
            }
            Node anchor = core.get(rng.nextInt(core.size()));
            double x = clamp(anchor.x() + (rng.nextDouble() - 0.5) * 60, MIN_X, MAX_X);
            double y = clamp(anchor.y() + (rng.nextDouble() - 0.5) * 60, MIN_Y, MAX_Y);
            if (inRiver(x, y) || inPark(x, y, rng)) {
                continue;
            }
            Node mid = city.addIntersection(x, y, "Lane-" + (i + 1));
            link(city, mid.id(), anchor.id(), RoadType.ALLEY, true);
            Node other = nearest(mid, core, 110);
            if (other != null && !other.id().equals(anchor.id())) {
                link(city, mid.id(), other.id(),
                        rng.nextDouble() < 0.4 ? RoadType.AVENUE : RoadType.ALLEY, true);
            }
            nodes.add(mid);
            core.add(mid);
        }
    }

    private static void densifyIndustrial(EditableCity city, List<Node> nodes, Random rng) {
        List<Node> yards = new ArrayList<>();
        for (Node n : nodes) {
            if (n.label().startsWith("Yard")) {
                yards.add(n);
            }
        }
        int extras = Math.min(16, yards.size() / 3);
        for (int i = 0; i < extras; i++) {
            if (yards.isEmpty()) {
                break;
            }
            Node a = yards.get(rng.nextInt(yards.size()));
            double x = clamp(a.x() + (rng.nextDouble() - 0.5) * 80, MIN_X, MAX_X);
            double y = clamp(a.y() + (rng.nextDouble() - 0.5) * 80, MIN_Y, MAX_Y);
            if (inRiver(x, y)) {
                continue;
            }
            Node mid = city.addIntersection(x, y, "Shed-" + (i + 1));
            link(city, mid.id(), a.id(), RoadType.AVENUE, true);
            nodes.add(mid);
            yards.add(mid);
        }
    }

    private static void addRingRoad(EditableCity city, List<Node> nodes, double cx, double cy, Random rng) {
        List<Node> ring = new ArrayList<>();
        double radius = 260 + rng.nextDouble() * 40;
        for (Node n : nodes) {
            double d = Math.hypot(n.x() - cx, n.y() - cy);
            if (Math.abs(d - radius) < 55) {
                ring.add(n);
            }
        }
        ring.sort((a, b) -> Double.compare(
                Math.atan2(a.y() - cy, a.x() - cx),
                Math.atan2(b.y() - cy, b.x() - cx)));
        for (int i = 0; i < ring.size(); i++) {
            Node a = ring.get(i);
            Node b = ring.get((i + 1) % ring.size());
            if (Math.hypot(a.x() - b.x(), a.y() - b.y()) < 160) {
                link(city, a.id(), b.id(), RoadType.HIGHWAY, true);
            }
        }
    }

    private static void addRiverBridges(EditableCity city, List<Node> nodes, Random rng) {
        List<Node> west = new ArrayList<>();
        List<Node> east = new ArrayList<>();
        for (Node n : nodes) {
            double rx = riverX(n.y());
            if (n.x() < rx - 30 && n.x() > rx - 120) {
                west.add(n);
            } else if (n.x() > rx + 30 && n.x() < rx + 120) {
                east.add(n);
            }
        }
        int bridges = Math.min(5, Math.min(west.size(), east.size()));
        west.sort((a, b) -> Double.compare(a.y(), b.y()));
        east.sort((a, b) -> Double.compare(a.y(), b.y()));
        for (int i = 0; i < bridges; i++) {
            int wi = (i * west.size()) / bridges;
            Node w = west.get(Math.min(wi, west.size() - 1));
            Node best = null;
            double bestD = 180;
            for (Node e : east) {
                double d = Math.hypot(w.x() - e.x(), w.y() - e.y());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best != null) {
                link(city, w.id(), best.id(), RoadType.HIGHWAY, true);
            }
        }
    }

    private static void addWaterfront(EditableCity city, List<Node> nodes, Random rng) {
        List<Node> bank = new ArrayList<>();
        for (Node n : nodes) {
            if (Math.abs(n.x() - riverX(n.y())) < 100) {
                bank.add(n);
            }
        }
        bank.sort((a, b) -> Double.compare(a.y(), b.y()));
        for (int i = 0; i + 1 < bank.size(); i++) {
            Node a = bank.get(i);
            Node b = bank.get(i + 1);
            if (Math.hypot(a.x() - b.x(), a.y() - b.y()) < 100 && rng.nextDouble() < 0.7) {
                link(city, a.id(), b.id(), RoadType.AVENUE, true);
            }
        }
    }

    private static void addArterialRibbons(
            EditableCity city,
            Map<Long, Node> cellIndex,
            int rows,
            int cols,
            Random rng
    ) {
        int[] rowBands = {rows / 5, rows / 2, (4 * rows) / 5};
        for (int r : rowBands) {
            Node prev = null;
            for (int c = 0; c < cols; c++) {
                Node n = cellIndex.get(pack(r, c));
                if (n == null) {
                    continue;
                }
                if (prev != null && city.findEdge(prev.id(), n.id()).isEmpty()) {
                    link(city, prev.id(), n.id(), RoadType.HIGHWAY, true);
                }
                prev = n;
            }
        }
        int[] colBands = {cols / 6, cols / 2, (5 * cols) / 6};
        for (int c : colBands) {
            Node prev = null;
            for (int r = 0; r < rows; r++) {
                Node n = cellIndex.get(pack(r, c));
                if (n == null) {
                    continue;
                }
                if (prev != null && city.findEdge(prev.id(), n.id()).isEmpty()) {
                    RoadType type = rng.nextDouble() < 0.55 ? RoadType.HIGHWAY : RoadType.AVENUE;
                    link(city, prev.id(), n.id(), type, true);
                }
                prev = n;
            }
        }
    }


    /** Undirected connectivity repair — link nearest nodes across components. */
    private static void stitchComponents(EditableCity city) {
        List<Node> nodes = new ArrayList<>(city.nodes());
        if (nodes.size() < 2) {
            return;
        }
        int guard = 0;
        while (guard++ < nodes.size()) {
            Map<NodeId, List<NodeId>> undirected = new HashMap<>();
            for (Node n : nodes) {
                undirected.put(n.id(), new ArrayList<>());
            }
            for (Edge e : city.edges()) {
                undirected.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
                undirected.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
            }
            List<List<Node>> components = componentsOf(nodes, undirected);
            if (components.size() <= 1) {
                return;
            }
            List<Node> aComp = components.get(0);
            List<Node> bComp = components.get(1);
            Node bestA = null;
            Node bestB = null;
            double best = Double.POSITIVE_INFINITY;
            for (Node a : aComp) {
                for (Node b : bComp) {
                    double d = Math.hypot(a.x() - b.x(), a.y() - b.y());
                    if (d < best) {
                        best = d;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            if (bestA == null || bestB == null) {
                return;
            }
            link(city, bestA.id(), bestB.id(), best > 140 ? RoadType.HIGHWAY : RoadType.AVENUE, true);
        }
    }

    private static List<List<Node>> componentsOf(List<Node> nodes, Map<NodeId, List<NodeId>> undirected) {
        Map<NodeId, Node> byId = new HashMap<>();
        for (Node n : nodes) {
            byId.put(n.id(), n);
        }
        Set<NodeId> seen = new HashSet<>();
        List<List<Node>> out = new ArrayList<>();
        for (Node start : nodes) {
            if (!seen.add(start.id())) {
                continue;
            }
            List<Node> comp = new ArrayList<>();
            Queue<NodeId> q = new ArrayDeque<>();
            q.add(start.id());
            while (!q.isEmpty()) {
                NodeId id = q.poll();
                Node n = byId.get(id);
                if (n != null) {
                    comp.add(n);
                }
                for (NodeId next : undirected.getOrDefault(id, List.of())) {
                    if (seen.add(next)) {
                        q.add(next);
                    }
                }
            }
            out.add(comp);
        }
        out.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return out;
    }

    private static void tryLink(EditableCity city, Node a, Node b, Random rng, boolean force) {
        if (a == null || b == null) {
            return;
        }
        if (!force && rng.nextDouble() < 0.20) {
            return;
        }
        double dist = Math.hypot(a.x() - b.x(), a.y() - b.y());
        RoadType type;
        if (dist > 130) {
            type = RoadType.HIGHWAY;
        } else if (dist > 85 || rng.nextDouble() < 0.5) {
            type = RoadType.AVENUE;
        } else {
            type = RoadType.ALLEY;
        }
        link(city, a.id(), b.id(), type, true);
    }

    private static void link(EditableCity city, NodeId a, NodeId b, RoadType type, boolean twoWay) {
        if (a.equals(b)) {
            return;
        }
        if (city.findEdge(a, b).isPresent()) {
            return;
        }
        if (twoWay) {
            city.connectTwoWay(a, b, type);
        } else {
            city.connectOneWay(a, b, type);
        }
    }

    private static Node nearestUnlinked(
            EditableCity city, Node a, List<Node> nodes, Random rng, double maxDist
    ) {
        List<Node> candidates = new ArrayList<>();
        for (Node b : nodes) {
            if (b.id().equals(a.id())) {
                continue;
            }
            double d = Math.hypot(a.x() - b.x(), a.y() - b.y());
            if (d > 40 && d < maxDist && city.findEdge(a.id(), b.id()).isEmpty()) {
                candidates.add(b);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(rng.nextInt(Math.min(6, candidates.size())));
    }

    private static Node nearest(Node a, List<Node> nodes, double maxDist) {
        Node best = null;
        double bestD = maxDist;
        for (Node b : nodes) {
            if (b.id().equals(a.id())) {
                continue;
            }
            double d = Math.hypot(a.x() - b.x(), a.y() - b.y());
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    private static List<Double> irregularAxes(double min, double max, int minCount, int maxCount, Random rng) {
        int count = minCount + rng.nextInt(Math.max(1, maxCount - minCount + 1));
        List<Double> axes = new ArrayList<>();
        double cursor = min;
        double remaining = max - min;
        for (int i = 0; i < count; i++) {
            axes.add(cursor);
            int left = count - i;
            double avg = remaining / left;
            double step = avg * (0.65 + rng.nextDouble() * 0.7);
            cursor += step;
            remaining = max - cursor;
            if (remaining < 40 && i < count - 1) {
                break;
            }
        }
        if (axes.get(axes.size() - 1) < max - 20) {
            axes.add(max);
        }
        return axes;
    }

    private static double riverX(double y) {
        return 380 + Math.sin(y / 90.0) * 55 + Math.sin(y / 40.0) * 18;
    }

    private static boolean inRiver(double x, double y) {
        return Math.abs(x - riverX(y)) < 28;
    }

    private static long pack(int r, int c) {
        return (((long) r) << 32) ^ (c & 0xffffffffL);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
