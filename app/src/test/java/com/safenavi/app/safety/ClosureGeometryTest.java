package com.safenavi.app.safety;

import static org.junit.Assert.*;
import com.safenavi.app.safety.network.NoGoGeometry;
import com.safenavi.app.safety.network.OfflineHazardCache;
import org.json.JSONObject;
import org.junit.Test;

public class ClosureGeometryTest {
    private static final double[][] POLYGON={{19.05,72.98},{19.05,73.02},{19.09,73.02},{19.09,72.98}};
    @Test public void pointClosureBlocksCrossingEdgeWithBothEndpointsOutside() {
        assertEquals(0,NoGoGeometry.distance(19.0657,72.9886,19.0657,73.0086,new double[][]{{19.0657,72.9986}},false),0.01);
        assertTrue(NoGoGeometry.distance(19.0757,72.9886,19.0757,73.0086,new double[][]{{19.0657,72.9986}},false)>60);
    }
    @Test public void polygonBlocksInteriorAndBoundaryCrossings() {
        assertEquals(0,NoGoGeometry.distance(19.07,73,19.071,73.001,POLYGON,true),0);
        assertEquals(0,NoGoGeometry.distance(19.07,72.96,19.07,73.04,POLYGON,true),0);
        assertTrue(NoGoGeometry.distance(19.11,72.96,19.11,73.04,POLYGON,true)>60);
    }
    @Test public void corridorIncludesIntersectionsAndCollinearOverlap() {
        assertEquals(0,NoGoGeometry.distance(19.07,72.99,19.07,73.01,new double[][]{{19.06,73},{19.08,73}},false),0);
        assertEquals(0,NoGoGeometry.distance(19.07,72.99,19.07,73.01,new double[][]{{19.07,73},{19.07,73.02}},false),0);
    }
    @Test public void snapshotUsesDefaultsAndIgnoresResolvedClosures() throws Exception {
        JSONObject h=new JSONObject().put("road_status","CLOSED").put("status","ACTIVE").put("severity","HIGH")
                .put("latitude",19.0657).put("longitude",72.9986).put("no_go_radius_m",JSONObject.NULL);
        JSONObject response=new JSONObject().put("hazards",new org.json.JSONArray().put(h));
        assertTrue(OfflineHazardCache.fromJson(response).blocksEdge(19.0657,72.9886,19.0657,73.0086));
        h.put("status","RESOLVED");
        assertFalse(OfflineHazardCache.fromJson(response).blocksEdge(19.0657,72.9886,19.0657,73.0086));
    }
}
