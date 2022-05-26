package me.cortex.cullmister.chunkBuilder;

import java.util.*;

public class QuadClusterizer {
    List<Quad> unique = new ArrayList<>();
    //TODO: Instead of a list of quads need to do a list of quad connections
    List<DirectionalQuadCluster> verticalClusters = new ArrayList<>();
    List<DirectionalQuadCluster> horizontalClusters = new ArrayList<>();
    List<List<Quad>> biClusters = new ArrayList<>();
    public void add(Quad quad) {
        //TODO: optimize this garbage with axis optimization
        int axis = quad.mergeabilityAxis();
        if (axis == -1) {
            unique.add(quad);
        } else if (axis == 0) {//Horizontal
            List<DirectionalQuadCluster> connectedCluster = new LinkedList<>();
            for (DirectionalQuadCluster cluster : horizontalClusters) {
                if (cluster.isJoinable(quad)) {
                    connectedCluster.add(cluster);
                }
            }
            if (connectedCluster.isEmpty()) {
                horizontalClusters.add(new DirectionalQuadCluster(quad, quad.mergeabilityAxis()));
            } else if (connectedCluster.size() == 1) {
                connectedCluster.get(0).add(quad);
            } else {
                if (connectedCluster.size() >2)
                    throw new IllegalStateException();
            }
        } else if (axis == 1) {//Vertical

            List<DirectionalQuadCluster> connectedCluster = new LinkedList<>();
            for (DirectionalQuadCluster cluster : verticalClusters) {
                if (cluster.isJoinable(quad)) {
                    connectedCluster.add(cluster);
                }
            }
            if (connectedCluster.isEmpty()) {
                verticalClusters.add(new DirectionalQuadCluster(quad, quad.mergeabilityAxis()));
            } else if (connectedCluster.size() == 1) {
                connectedCluster.get(0).add(quad);
            } else {
                if (connectedCluster.size() >2)
                    throw new IllegalStateException();
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
