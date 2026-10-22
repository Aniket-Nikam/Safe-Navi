package com.safenavi.app.safety;

import static org.junit.Assert.*;
import com.safenavi.app.safety.network.OfflineHazardCache;
import com.safenavi.app.safety.service.HazardPassability;
import org.json.*;
import org.junit.Test;

public class HazardPassabilityTest {
    private JSONObject flood() throws Exception {
        return new JSONObject().put("category_key","FLOODING").put("severity","LOW").put("road_status","CAUTION")
                .put("status","ACTIVE").put("latitude",19.0657).put("longitude",72.9986);
    }
    @Test public void legacyAndUnknownFloodsAreExcludedOffline() throws Exception {
        JSONObject h=flood();assertTrue(HazardPassability.restricted(h));
        assertTrue(HazardPassability.floodLabel(h).contains("unconfirmed"));
        JSONObject response=new JSONObject().put("hazards",new JSONArray().put(h));
        assertTrue(OfflineHazardCache.fromJson(response).blocksEdge(19.0657,72.9886,19.0657,73.0086));
        assertFalse(OfflineHazardCache.fromJson(response).blocksEdge(19.0757,72.9886,19.0757,73.0086));
    }
    @Test public void explicitPassabilityAllowsCautionButNeverOverridesClosure() throws Exception {
        JSONObject h=flood().put("passability","PASSABLE");assertFalse(HazardPassability.restricted(h));
        assertTrue(HazardPassability.floodLabel(h).contains("passable with caution"));
        h.put("road_status","CLOSED");assertTrue(HazardPassability.restricted(h));
        h.put("road_status","UNSAFE");assertTrue(HazardPassability.restricted(h));
    }
    @Test public void impassableAndResolvedFloodsHaveDifferentOfflineEffects() throws Exception {
        JSONObject h=flood().put("passability","IMPASSABLE");
        JSONObject response=new JSONObject().put("hazards",new JSONArray().put(h));
        assertTrue(OfflineHazardCache.fromJson(response).blocksEdge(19.0657,72.9886,19.0657,73.0086));
        h.put("status","RESOLVED");assertFalse(OfflineHazardCache.fromJson(response).blocksEdge(19.0657,72.9886,19.0657,73.0086));
    }
    @Test public void nonFloodCautionDoesNotBecomeAnAutomaticClosure() throws Exception {
        assertFalse(HazardPassability.restricted(flood().put("category_key","CRIME")));
    }
}
