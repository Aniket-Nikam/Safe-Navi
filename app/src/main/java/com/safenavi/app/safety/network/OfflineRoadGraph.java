package com.safenavi.app.safety.network;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Immutable primitive adjacency arrays, shared by routing requests. */
final class OfflineRoadGraph {
    final double[] lat, lon;
    final int[] head, next, to;
    final float[] distance, risk, drive, walk, cycle;
    final double[] maxSpeed = {1, 1, 1};
    final int[] access;
    private OfflineRoadGraph(int nodes, int edges) {
        lat=new double[nodes];lon=new double[nodes];head=new int[nodes];access=new int[nodes]; Arrays.fill(head,-1);
        next=new int[edges];to=new int[edges];distance=new float[edges];risk=new float[edges];
        drive=new float[edges];walk=new float[edges];cycle=new float[edges];
    }
    static OfflineRoadGraph load(SQLiteDatabase db) {
        int nodes=count(db,"nodes"), edges=count(db,"edges");
        OfflineRoadGraph graph=new OfflineRoadGraph(nodes,edges);
        Map<Long,Integer> index=new HashMap<>(nodes*2);
        try (Cursor c=db.rawQuery("SELECT node_id,latitude,longitude FROM nodes",null)) {
            int i=0; while(c.moveToNext()) {index.put(c.getLong(0),i);graph.lat[i]=c.getDouble(1);graph.lon[i]=c.getDouble(2);i++;}
        }
        try (Cursor c=db.rawQuery("SELECT from_node,to_node,distance_m,risk,drive_seconds,walk_seconds,cycle_seconds FROM edges",null)) {
            int i=0; while(c.moveToNext()) {
                Integer from=index.get(c.getLong(0)), to=index.get(c.getLong(1));
                if(from == null || to == null) throw new IllegalStateException("Offline graph contains an invalid edge");
                graph.to[i]=to;graph.next[i]=graph.head[from];graph.head[from]=i;
                graph.distance[i]=c.getFloat(2);graph.risk[i]=c.getFloat(3);
                graph.drive[i]=c.isNull(4)?Float.NaN:c.getFloat(4);
                graph.walk[i]=c.isNull(5)?Float.NaN:c.getFloat(5);
                graph.cycle[i]=c.isNull(6)?Float.NaN:c.getFloat(6);
                for(int mode=0;mode<3;mode++) {
                    float seconds=graph.times(mode)[i];
                    if(Float.isFinite(seconds) && seconds>0) {
                        graph.access[from]|=1<<mode;graph.access[to]|=1<<mode;
                        graph.maxSpeed[mode]=Math.max(graph.maxSpeed[mode],graph.distance[i]/seconds);
                    }
                }
                i++;
            }
        }
        return graph;
    }
    float[] times(int mode) {return mode==1?walk:mode==2?cycle:drive;}
    int nearest(double latitude,double longitude,int mode) {
        int best=-1; double score=Double.POSITIVE_INFINITY;
        for(int i=0;i<lat.length;i++) {
            if((access[i] & (1<<mode))==0 || Math.abs(latitude-lat[i])>0.08 || Math.abs(longitude-lon[i])>0.08) continue;
            double d=(latitude-lat[i])*(latitude-lat[i])+(longitude-lon[i])*(longitude-lon[i]);
            if(d<score){score=d;best=i;}
        }
        return best;
    }
    private static int count(SQLiteDatabase db,String table) {
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}
    }
}
