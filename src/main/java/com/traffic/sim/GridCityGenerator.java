package com.traffic.sim;

import com.traffic.config.CityGenConfig;
import com.traffic.model.graph.EditableCity;
import com.traffic.model.graph.Node;
import com.traffic.model.graph.NodeId;

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
                double x = c * config.spacing();
                double y = r * config.spacing();
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
        if (config.twoWayStreets()) {
            city.connectTwoWay(a, b, config.defaultCapacity());
        } else {
            city.connectOneWay(a, b, config.defaultCapacity());
        }
    }

    /** Playful but scannable labels for big maps: R2C7, etc. */
    static String streetName(int row, int col) {
        return "R" + row + "C" + col;
    }
}
