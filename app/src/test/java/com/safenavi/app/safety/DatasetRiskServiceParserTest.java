package com.safenavi.app.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.safenavi.app.safety.model.DatasetPointRisk;
import com.safenavi.app.safety.model.DatasetRouteEvaluation;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.network.DatasetRiskService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DatasetRiskServiceParserTest {
    @Test public void parsesPointRisk() throws Exception {
        DatasetPointRisk risk = DatasetRiskService.parsePointRisk(
                "{\"risk_score\":48.5,\"safety_score\":51.5,\"risk_label\":\"high\","
                        + "\"area_name\":\"Vashi\",\"time_period\":\"evening_peak\"}");
        assertEquals(51.5, risk.getSafetyScore(), 0.01);
        assertEquals("Vashi", risk.getAreaName());
    }

    @Test public void parsesAndRanksDatasetRoutes() throws Exception {
        RouteCandidate first = route("r1", 20);
        RouteCandidate second = route("r2", 21);
        String body = "{\"time_period\":\"night\",\"results\":["
                + "{\"route_id\":\"r2\",\"risk_exposure\":30,\"ranking_score\":31.5,"
                + "\"coverage_ratio\":1,\"factor_averages\":{\"crime_risk_index\":70,"
                + "\"lighting_quality_index\":20,\"traffic_congestion_index\":10}},"
                + "{\"route_id\":\"r1\",\"risk_exposure\":60,\"ranking_score\":41}]}";
        DatasetRouteEvaluation result = DatasetRiskService.parseRouteEvaluation(body,
                Arrays.asList(first, second), RouteProfile.BALANCED);
        assertEquals("r2", result.getRankedResults().get(0).getRoute().getProviderRouteId());
        assertEquals(1.0, result.getCoverageRatio(), 0.001);
        assertTrue(result.getFactorSummary().contains("crime risk"));
    }

    @Test public void parsesBundledTrainedModelMetadata() throws Exception {
        RouteCandidate route = route("ml-route", 18);
        String body = "{\"time_period\":\"evening_peak\",\"risk_provider\":\"trained_ml\",\"results\":["
                + "{\"route_id\":\"ml-route\",\"risk_exposure\":46.2,\"ranking_score\":34.17,"
                + "\"coverage_ratio\":0.95,\"model_version\":\"safe-route-linear-2026.10\","
                + "\"model_confidence\":0.797,\"model_factor_contributions\":{"
                + "\"crime_risk_index\":18.4,\"lighting_quality_index\":-8.1,\"isolation_index\":7.2}}]}";
        DatasetRouteEvaluation result = DatasetRiskService.parseRouteEvaluation(body,
                Collections.singletonList(route), RouteProfile.BALANCED);
        assertEquals("trained_ml", result.getProvider());
        assertEquals("safe-route-linear-2026.10", result.getModelVersion());
        assertEquals(0.797, result.getConfidence(), 0.001);
        assertTrue(result.getFactorSummary().contains("crime risk"));
    }

    private RouteCandidate route(String id, double minutes) {
        return new RouteCandidate(id, minutes, 1000, Collections.singletonList(
                new HazardLocation.GeoPoint(19.0, 73.0)));
    }
}
