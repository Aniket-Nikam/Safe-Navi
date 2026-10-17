package com.safenavi.app.safety.network;

import com.safenavi.app.BuildConfig;
import com.safenavi.app.safety.model.DatasetPointRisk;
import com.safenavi.app.safety.model.DatasetRouteEvaluation;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.RouteSafetyResult;
import com.safenavi.app.safety.model.RouteRiskPoint;
import com.safenavi.app.safety.model.RouteRiskSection;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Client for the Safe-Navi baseline and verified-hazard risk service. */
public class DatasetRiskService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build();

    public interface ResultCallback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public void evaluateRoutes(List<RouteCandidate> candidates, RouteProfile profile,
                               String timePeriod, ResultCallback<DatasetRouteEvaluation> callback) {
        HttpUrl url = HttpUrl.parse(trimSlash(com.safenavi.app.product.ServerConnection.baseUrl()) + "/api/v1/risk/route");
        if (url == null) {
            callback.onError("Invalid dataset service URL.");
            return;
        }
        try {
            JSONObject payload = buildRoutePayload(candidates, profile, timePeriod);
            Request request = new Request.Builder().url(url).header("Accept", "application/json")
                    .post(RequestBody.create(payload.toString(), JSON)).build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException error) {
                    callback.onError("Dataset API unavailable.");
                }

                @Override public void onResponse(Call call, Response response) {
                    try (Response closeable = response) {
                        String body = response.body() == null ? "" : response.body().string();
                        if (!response.isSuccessful()) {
                            try { JSONObject detail = new JSONObject(body).optJSONObject("detail");
                                callback.onError(detail == null ? "Dataset API returned HTTP " + response.code() + "." : detail.optString("message", "No confirmed safe alternative is available."));
                            } catch (Exception ignored) { callback.onError("Dataset API returned HTTP " + response.code() + "."); }
                            return;
                        }
                        callback.onSuccess(parseRouteEvaluation(body, candidates, profile));
                    } catch (Exception error) {
                        callback.onError("Dataset API returned an unreadable response.");
                    }
                }
            });
        } catch (JSONException error) {
            callback.onError("Could not build the dataset request.");
        }
    }

    public void pointRisk(double latitude, double longitude, String timePeriod,
                          ResultCallback<DatasetPointRisk> callback) {
        HttpUrl base = HttpUrl.parse(trimSlash(com.safenavi.app.product.ServerConnection.baseUrl()) + "/api/v1/risk/point");
        if (base == null) {
            callback.onError("Invalid dataset service URL.");
            return;
        }
        HttpUrl url = base.newBuilder().addQueryParameter("latitude", Double.toString(latitude))
                .addQueryParameter("longitude", Double.toString(longitude))
                .addQueryParameter("time_period", timePeriod).build();
        client.newCall(new Request.Builder().url(url).header("Accept", "application/json").build())
                .enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException error) {
                        callback.onError("Dataset API unavailable.");
                    }

                    @Override public void onResponse(Call call, Response response) {
                        try (Response closeable = response) {
                            if (!response.isSuccessful() || response.body() == null) {
                                callback.onError(response.code() == 404 ? "OUTSIDE_COVERAGE" : "Safety API returned HTTP " + response.code());
                                return;
                            }
                            callback.onSuccess(parsePointRisk(response.body().string()));
                        } catch (Exception error) {
                            callback.onError("Dataset API returned an unreadable response.");
                        }
                    }
                });
    }

    public static DatasetPointRisk parsePointRisk(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        return new DatasetPointRisk(root.getDouble("risk_score"), root.getDouble("safety_score"),
                root.getString("risk_label"), root.optString("area_name", "Dataset area"),
                root.getString("time_period"));
    }

    public static DatasetRouteEvaluation parseRouteEvaluation(String body,
                                                               List<RouteCandidate> candidates,
                                                               RouteProfile profile) throws JSONException {
        JSONObject root = new JSONObject(body);
        Map<String, RouteCandidate> byId = new HashMap<>();
        for (RouteCandidate candidate : candidates) byId.put(candidate.getProviderRouteId(), candidate);
        List<RouteSafetyResult> ranked = new ArrayList<>();
        double coverage = 0;
        String factorSummary = "10-factor baseline dataset";
        String provider = root.optString("risk_provider", "deterministic_baseline");
        String modelVersion = "";
        double confidence = 0;
        JSONArray results = root.getJSONArray("results");
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            RouteCandidate route = byId.get(item.getString("route_id"));
            if (route == null) continue;
            List<RouteRiskPoint> riskPoints = new ArrayList<>();
            JSONArray zones = item.optJSONArray("risk_points");
            if (zones != null) for (int j = 0; j < zones.length(); j++) {
                JSONObject zone = zones.optJSONObject(j);
                if (zone == null) continue;
                double lat = zone.optDouble("latitude", Double.NaN), lon = zone.optDouble("longitude", Double.NaN);
                if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
                riskPoints.add(new RouteRiskPoint(zone.optString("id"), lat, lon, zone.optString("title"),
                        zone.optString("reason"), zone.optDouble("risk_exposure", 0), zone.optBoolean("verified", false), zone.optString("source")));
            }
            List<RouteRiskSection> sections=new ArrayList<>(); JSONArray sectionArray=item.optJSONArray("risk_sections");
            if(sectionArray!=null) for(int j=0;j<sectionArray.length();j++) {
                JSONObject section=sectionArray.optJSONObject(j); if(section==null) continue;
                JSONArray points=section.optJSONArray("coordinates"); List<HazardLocation.GeoPoint> shape=new ArrayList<>();
                if(points!=null) for(int k=0;k<points.length();k++) {
                    JSONObject point=points.optJSONObject(k); if(point==null) continue;
                    double lat=point.optDouble("latitude",Double.NaN), lon=point.optDouble("longitude",Double.NaN);
                    if(Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180) shape.add(new HazardLocation.GeoPoint(lat,lon));
                }
                if(shape.size()<2) continue;
                sections.add(new RouteRiskSection(section.optString("id"),section.optString("band","unknown"),
                        section.optString("area_name"),section.optString("reason"),section.optString("source"),section.optDouble("risk_exposure",Double.NaN),
                        section.optDouble("latitude"),section.optDouble("longitude"),section.optDouble("distance_from_start_m"),section.optDouble("length_m"),shape));
            }
            ranked.add(new RouteSafetyResult(route, profile, item.getDouble("risk_exposure"),
                    item.getDouble("ranking_score"), riskPoints, sections));
            if (i == 0) {
                coverage = item.optDouble("coverage_ratio", 0);
                if (provider.endsWith("_ml")) {
                    factorSummary = topFactors(item.optJSONObject("model_factor_contributions"));
                    modelVersion = item.optString("model_version"); confidence = item.optDouble("model_confidence", 0);
                } else factorSummary = topFactors(item.optJSONObject("factor_averages"));
            }
        }
        ranked.sort(Comparator.comparingDouble(RouteSafetyResult::getScore));
        return new DatasetRouteEvaluation(ranked, root.getString("time_period"), coverage, factorSummary,
                provider, modelVersion, confidence);
    }

    private static String topFactors(JSONObject factors) throws JSONException {
        if (factors == null) return "10-factor baseline dataset";
        List<Map.Entry<String, Double>> values = new ArrayList<>();
        Iterator<String> keys = factors.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            values.add(new AbstractMap.SimpleEntry<>(key, factors.getDouble(key)));
        }
        values.sort((left, right) -> Double.compare(right.getValue(), left.getValue()));
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < Math.min(3, values.size()); i++) {
            String label = values.get(i).getKey().replace("_index", "").replace('_', ' ');
            labels.add(label + " " + Math.round(values.get(i).getValue()));
        }
        StringBuilder joined = new StringBuilder();
        for (String label : labels) {
            if (joined.length() > 0) joined.append(" · ");
            joined.append(label);
        }
        return joined.toString();
    }

    public static JSONObject buildRoutePayload(List<RouteCandidate> candidates, RouteProfile profile,
                                               String timePeriod) throws JSONException {
        JSONObject payload = new JSONObject().put("time_period", timePeriod)
                .put("profile", profile.name().toLowerCase(Locale.US));
        JSONArray routes = new JSONArray();
        for (RouteCandidate candidate : candidates) {
            JSONObject route = new JSONObject().put("route_id", candidate.getProviderRouteId())
                    .put("travel_minutes", candidate.getTravelMinutes()).put("distance_meters", candidate.getDistanceMeters());
            JSONArray coordinates = new JSONArray();
            // Full geometry is mandatory for closure checks. Server samples only ML features.
            for (HazardLocation.GeoPoint point : candidate.getGeometry())
                coordinates.put(new JSONObject().put("latitude", point.getLatitude()).put("longitude", point.getLongitude()));
            routes.put(route.put("coordinates", coordinates));
        }
        return payload.put("routes", routes);
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
