package com.traffic.sim;

import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dense, irregular megacity — busy wards, crooked lanes, arterial spines,
 * and a river gap (Kolkata-flavoured, not a perfect grid).
 */
public final class OrganicCityGenerator {

    private static final double MIN_X = 60;
    private static final double MIN_Y = 50;
    private static final double MAX_X = 1340;
    private static final double MAX_Y = 820;

    private OrganicCityGenerator() {
    }

    public static EditableCity generate(long seed) {
        Random rng = new Random(seed);
        EditableCity city = new EditableCity();
        List<Node> nodes = new ArrayList<>();
        Map<Long, Node> cellIndex = new HashMap<>();

        // Irregular block spacing (wards of uneven size).
        List<Double> xs = irregularAxes(MIN_X, MAX_X, 14, 18, rng);
        List<Double> ys = irregularAxes(MIN_Y, MAX_Y, 11, 15, rng);

        for (int r = 0; r < ys.size(); r++) {
            for (int c = 0; c < xs.size(); c++) {
                double x = xs.get(c) + (rng.nextDouble() - 0.5) * 22;
                double y = ys.get(r) + (rng.nextDouble() - 0.5) * 22;
                x = clamp(x, MIN_X, MAX_X);
                y = clamp(y, MIN_Y, MAX_Y);
                // River Hooghly-ish curve: punch a soft gap through the west-center.
                if (inRiver(x, y)) {
                    continue;
                }
                // Drop some intersections so the fabric isn't uniform.
                if (rng.nextDouble() < 0.08) {
                    continue;
                }
                String label = wardLabel(r, c);
                Node node = city.addIntersection(x, y, label);
                nodes.add(node);
                cellIndex.put(pack(r, c), node);
            }
        }

        // Orthogonal links with missing lanes + type mix.
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

        // Crooked diagonal shortcuts (bazaar cut-throughs).
        int diagonals = Math.max(12, nodes.size() / 18);
        for (int i = 0; i < diagonals; i++) {
            Node a = nodes.get(rng.nextInt(nodes.size()));
            Node b = nearestUnlinked(city, a, nodes, rng, 180);
            if (b != null) {
                link(city, a.id(), b.id(), RoadType.ALLEY, true);
            }
        }

        // Major arterials: connect across longer spans E-W / N-S near ward centers.
        addArterialRibbons(city, cellIndex, ys.size(), xs.size(), rng);

        // Dense core: extra mid-block nodes near the center.
        densifyCore(city, nodes, rng);

        city.drainChanges();
        return city;
    }

    private static void densifyCore(EditableCity city, List<Node> nodes, Random rng) {
        double cx = (MIN_X + MAX_X) / 2;
        double cy = (MIN_Y + MAX_Y) / 2;
        List<Node> core = new ArrayList<>();
        for (Node n : nodes) {
            if (Math.hypot(n.x() - cx, n.y() - cy) < 220) {
                core.add(n);
            }
        }
        int extras = Math.min(28, Math.max(8, core.size() / 4));
        for (int i = 0; i < extras; i++) {
            if (core.isEmpty()) {
                break;
            }
            Node anchor = core.get(rng.nextInt(core.size()));
            double x = clamp(anchor.x() + (rng.nextDouble() - 0.5) * 70, MIN_X, MAX_X);
            double y = clamp(anchor.y() + (rng.nextDouble() - 0.5) * 70, MIN_Y, MAX_Y);
            if (inRiver(x, y)) {
                continue;
            }
            Node mid = city.addIntersection(x, y, "Lane-" + (i + 1));
            link(city, mid.id(), anchor.id(), RoadType.ALLEY, true);
            Node other = nearest(mid, core, 120);
            if (other != null && !other.id().equals(anchor.id())) {
                link(city, mid.id(), other.id(), RoadType.ALLEY, true);
            }
            nodes.add(mid);
            core.add(mid);
        }
    }

    private static void addArterialRibbons(
            EditableCity city,
            Map<Long, Node> cellIndex,
            int rows,
            int cols,
            Random rng
    ) {
        int[] rowBands = {rows / 4, rows / 2, (3 * rows) / 4};
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
        int[] colBands = {cols / 5, cols / 2, (4 * cols) / 5};
        for (int c : colBands) {
            Node prev = null;
            for (int r = 0; r < rows; r++) {
                Node n = cellIndex.get(pack(r, c));
                if (n == null) {
                    continue;
                }
                if (prev != null && city.findEdge(prev.id(), n.id()).isEmpty()) {
                    RoadType type = rng.nextDouble() < 0.5 ? RoadType.HIGHWAY : RoadType.AVENUE;
                    link(city, prev.id(), n.id(), type, true);
                }
                prev = n;
            }
        }
    }

    private static void tryLink(EditableCity city, Node a, Node b, Random rng, boolean force) {
        if (a == null || b == null) {
            return;
        }
        if (!force && rng.nextDouble() < 0.22) {
            return; // missing street — organic gaps
        }
        double dist = Math.hypot(a.x() - b.x(), a.y() - b.y());
        RoadType type;
        if (dist > 130) {
            type = RoadType.HIGHWAY;
        } else if (dist > 85 || rng.nextDouble() < 0.55) {
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

    /** Soft river corridor snaking north-south. */
    private static boolean inRiver(double x, double y) {
        double riverX = 380 + Math.sin(y / 90.0) * 55 + Math.sin(y / 40.0) * 18;
        return Math.abs(x - riverX) < 28;
    }

    private static String wardLabel(int r, int c) {
        return "W" + r + "-" + c;
    }

    private static long pack(int r, int c) {
        return (((long) r) << 32) ^ (c & 0xffffffffL);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
