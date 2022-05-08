package me.cortex.cullmister.chunkBuilder;

import com.ibm.icu.impl.Pair;

import java.util.*;

public class QuadClusterizer {
    List<Quad> unique = new ArrayList<>();
    List<List<Quad>> verticalClusters = new ArrayList<>();
    List<List<Quad>> horizontalClusters = new ArrayList<>();
    List<List<Quad>> biClusters = new ArrayList<>();
    public void add(Quad quad) {
        //TODO: optimize this garbage with axis optimization
        int axis = quad.mergeabilityAxis();
        if (axis == -1) {
            unique.add(quad);
        } else if (axis == 0) {//Horizontal
            List<List<Quad>> connectedCluster = new LinkedList<>();
            for (List<Quad> cluster : horizontalClusters) {
                for (Quad quadNew : cluster) {
                    if (quad.connectable(quadNew) != -1) {
                        connectedCluster.add(cluster);
                        break;
                    }
                }
            }
            if (connectedCluster.isEmpty()) {
                horizontalClusters.add(new ArrayList<>(List.of(quad)));
            } else if (connectedCluster.size() == 1) {
                connectedCluster.get(0).add(quad);
            } else {
                horizontalClusters.removeAll(connectedCluster);
                List<Quad> merged = new ArrayList<>();
                connectedCluster.forEach(merged::addAll);
                merged.add(quad);
                horizontalClusters.add(merged);
            }
        } else if (axis == 1) {//Vertical
            List<List<Quad>> connectedCluster = new LinkedList<>();
            for (List<Quad> cluster : verticalClusters) {
                for (Quad quadNew : cluster) {
                    if (quad.connectable(quadNew) != -1) {
                        connectedCluster.add(cluster);
                        break;
                    }
                }
            }
            if (connectedCluster.isEmpty()) {
                verticalClusters.add(new ArrayList<>(List.of(quad)));
            } else if (connectedCluster.size() == 1) {
                connectedCluster.get(0).add(quad);
            } else {
                verticalClusters.removeAll(connectedCluster);
                List<Quad> merged = new ArrayList<>();
                connectedCluster.forEach(merged::addAll);
                merged.add(quad);
                verticalClusters.add(merged);
            }
        } else if (axis == 2) {//Bi
            List<List<Quad>> connectedCluster = new LinkedList<>();
            for (List<Quad> cluster : biClusters) {
                for (Quad quadNew : cluster) {
                    if (quad.connectable(quadNew) != -1) {
                        connectedCluster.add(cluster);
                        break;
                    }
                }
            }
            if (connectedCluster.isEmpty()) {
                biClusters.add(new ArrayList<>(List.of(quad)));
            } else if (connectedCluster.size() == 1) {
                connectedCluster.get(0).add(quad);
            } else {
                biClusters.removeAll(connectedCluster);
                List<Quad> merged = new ArrayList<>();
                connectedCluster.forEach(merged::addAll);
                merged.add(quad);
                biClusters.add(merged);
            }
        }
    }

    public boolean isEmpty() {
        return unique.isEmpty() && verticalClusters.isEmpty() && horizontalClusters.isEmpty() && biClusters.isEmpty();
    }
}
