package me.cortex.cullmister.chunkBuilder;

import java.util.*;

public class QuadOptimizer {
    public static List<Quad> optimize(QuadClusterizer clusters) {
        List<Quad> out = new ArrayList<>(clusters.unique);

        Iterator<DirectionalQuadCluster> clusterI = clusters.horizontalClusters.iterator();
        while (clusterI.hasNext()){
            DirectionalQuadCluster cluster = clusterI.next();
            if (cluster.quads.size() == 1) {
                out.addAll(cluster.quads);
                clusterI.remove();
            }
        }
        clusterI = clusters.verticalClusters.iterator();
        while (clusterI.hasNext()){
            DirectionalQuadCluster cluster = clusterI.next();
            if (cluster.quads.size() == 1) {
                out.addAll(cluster.quads);
                clusterI.remove();
            }
        }
        /*
        clusterI = clusters.biClusters.iterator();
        while (clusterI.hasNext()){
            List<Quad> lcluster = clusterI.next();
            if (lcluster.size() == 1) {
                //out.addAll(lcluster);
                clusterI.remove();
            }
        }

        for(List<Quad> cluster :  clusters.horizontalClusters) {
            cluster.sort(Comparator.comparingDouble(a-> Arrays.stream(a.corners).mapToDouble(b->b.pos().distanceSquared(0,0,0)).sum()));
            out.addAll(cluster);
        }

        for(List<Quad> cluster :  clusters.verticalClusters) {
            out.addAll(cluster);
        }
        clusters.biClusters.forEach(a->out.addAll(reduce(a)));
        */
        /*
        for(DirectionalQuadCluster cluster :  clusters.horizontalClusters) {
            Vert[] c = new Vert[4];
            int a = cluster.quads.get(0).connectable(cluster.quads.get(1));
            int b = Quad.OPPOSITE_FACE[a];
            int[] A = Quad.FACE2INDEX[a];
            int[] B = Quad.FACE2INDEX[b];
            c[B[0]] = cluster.quads.get(0).corners[B[0]];
            c[B[1]] = cluster.quads.get(0).corners[B[1]];
            c[A[0]] = cluster.quads.get(cluster.quads.size()-1).corners[A[0]];
            c[A[1]] = cluster.quads.get(cluster.quads.size()-1).corners[A[1]];
            out.add(new Quad(c , cluster.quads.get(0).sprite));
        }*/
        clusters.verticalClusters.forEach(a->{{out.addAll(a.quads);}});
        clusters.horizontalClusters.forEach(a->{out.addAll(a.quads);});
        clusters.biClusters.forEach(a->{out.addAll(a);});
        return out;
    }




    public static List<Quad> reduce(List<Quad> quads) {
        return quads;
    }
}
