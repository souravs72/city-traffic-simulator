package com.traffic.sim;

import com.traffic.config.CityGenConfig;
import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;
import com.traffic.model.graph.RoadType;

/**
 * Builds a large rectangular street grid into an {@link EditableCity}
 * so the UI can keep editing afterward (draw shortcuts, bulldoze, etc.).
 */
public final class GridCityGenerator {

    private GridCityGenerator() {
    }

    public static EditableCity generate(CityGenConfig config) {
        EditableCity city = new EditableCity();
        Node[][] grid = new Node[config.rows()][config.cols()];

        for (int r = 0; r < config.rows(); r++) {
            for (int c = 0; c < config.cols(); c++) {
                String label = streetName(r, c);
                // Margin keeps presets away from canvas edges on the larger map.
                double x = 80 + c * config.spacing();
                double y = 70 + r * config.spacing();
                grid[r][c] = city.addIntersection(x, y, label);
            }
        }

        for (int r = 0; r < config.rows(); r++) {
            for (int c = 0; c < config.cols(); c++) {
                NodeId here = grid[r][c].id();
                if (c + 1 < config.cols()) {
                    link(city, here, grid[r][c + 1].id(), config);
                }
                if (r + 1 < config.rows()) {
                    link(city, here, grid[r + 1][c].id(), config);
                }
            }
        }

        city.drainChanges(); // generation isn't "user edits"
        return city;
    }

    private static void link(EditableCity city, NodeId a, NodeId b, CityGenConfig config) {
        RoadType type = arterialType(city, a, b, config);
        if (config.twoWayStreets()) {
            city.connectTwoWay(a, b, type);
        } else {
            city.connectOneWay(a, b, type);
        }
    }

    /** Outer ring → highway, mid city → avenue, tiny grids stay avenues. */
    private static RoadType arterialType(EditableCity city, NodeId a, NodeId b, CityGenConfig config) {
        if (config.rows() <= 3 && config.cols() <= 3) {
            return RoadType.AVENUE;
        }
        var na = city.node(a).orElseThrow();
        var nb = city.node(b).orElseThrow();
        double minX = 80;
        double minY = 70;
        double maxX = 80 + (config.cols() - 1) * config.spacing();
        double maxY = 70 + (config.rows() - 1) * config.spacing();
        boolean border = near(na.x(), minX) || near(na.x(), maxX) || near(na.y(), minY) || near(na.y(), maxY)
                || near(nb.x(), minX) || near(nb.x(), maxX) || near(nb.y(), minY) || near(nb.y(), maxY);
        if (border) {
            return RoadType.HIGHWAY;
        }
        if (config.rows() >= 12) {
            return RoadType.AVENUE;
        }
        return RoadType.AVENUE;
    }

    private static boolean near(double v, double target) {
        return Math.abs(v - target) < 1e-6;
    }

    /** Playful but scannable labels for big maps: R2C7, etc. */
    static String streetName(int row, int col) {
        return "R" + row + "C" + col;
    }
}
