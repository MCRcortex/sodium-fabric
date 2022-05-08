package me.cortex.cullmister.chunkBuilder;

import java.util.*;

public class QuadOptimizer {
    public static List<Quad> optimize(QuadClusterizer clusters) {
        List<Quad> out = new ArrayList<>(clusters.unique);

        Iterator<List<Quad>> clusterI = clusters.horizontalClusters.iterator();
        while (clusterI.hasNext()){
            List<Quad> lcluster = clusterI.next();
            if (lcluster.size() == 1) {
                out.addAll(lcluster);
                clusterI.remove();
            }
        }
        clusterI = clusters.verticalClusters.iterator();
        while (clusterI.hasNext()){
            List<Quad> lcluster = clusterI.next();
            if (lcluster.size() == 1) {
                out.addAll(lcluster);
                clusterI.remove();
            }
        }
        clusterI = clusters.biClusters.iterator();
        while (clusterI.hasNext()){
            List<Quad> lcluster = clusterI.next();
            if (lcluster.size() == 1) {
                out.addAll(lcluster);
                clusterI.remove();
            }
        }

        for(List<Quad> cluster :  clusters.horizontalClusters) {
            cluster.sort(Comparator.comparingDouble(a-> Arrays.stream(a.corners).mapToDouble(b->b.pos().distanceSquared(0,0,0)).sum()));

        }

        for(List<Quad> cluster :  clusters.verticalClusters) {

        }


        clusters.biClusters.forEach(a->out.addAll(reduce(a)));
        return out;
    }




    public static List<Quad> reduce(List<Quad> quads) {
        return quads;
    }
}
