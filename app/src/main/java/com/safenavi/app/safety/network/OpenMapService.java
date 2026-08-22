package com.safenavi.app.safety.network;

import android.os.Handler;
import android.os.Looper;
import com.safenavi.app.BuildConfig;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.TravelMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small provider boundary for OSM-backed search and routing demo services. */
public class OpenMapService {
    private static final String USER_AGENT = "Safe-Navi-College-Demo/1.0 (github.com/Aniket-Nikam/Safe-Navi)";
    private static final long NOMINATIM_INTERVAL_MS = 1_100L;
    private final OkHttpClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastSearchAt;

    public OpenMapService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public interface ResultCallback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    /** Explicit submit only: never call this for type-ahead/autocomplete. */
    public synchronized void search(String query, ResultCallback<List<PlaceSearchResult>> callback) {
        long now = System.currentTimeMillis();
        long delay = Math.max(0, NOMINATIM_INTERVAL_MS - (now - lastSearchAt));
        lastSearchAt = now + delay;
        handler.postDelayed(() -> executeSearch(query, callback), delay);
    }

    private void executeSearch(String query, ResultCallback<List<PlaceSearchResult>> callback) {
        HttpUrl base = HttpUrl.parse(BuildConfig.NOMINATIM_BASE_URL);
        if (base == null) {
            callback.onError("Invalid geocoding service configuration.");
            return;
        }
        HttpUrl url = base.newBuilder()
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("format", "jsonv2")
                .addQueryParameter("limit", "5")
                .addQueryParameter("countrycodes", "in")
                .addQueryParameter("accept-language", "en")
                .build();
        execute(url, body -> parsePlaces(body), callback);
    }

    public void routes(double fromLat, double fromLon, double toLat, double toLon,
                       TravelMode mode, ResultCallback<List<RouteCandidate>> callback) {
        HttpUrl parsed = HttpUrl.parse(trimSlash(BuildConfig.VALHALLA_BASE_URL) + "/route");
        if (parsed == null) {
            callback.onError("Invalid routing service configuration.");
            return;
        }
        try {
            JSONObject request = new JSONObject();
            request.put("locations", new JSONArray()
                    .put(new JSONObject().put("lat", fromLat).put("lon", fromLon))
                    .put(new JSONObject().put("lat", toLat).put("lon", toLon)));
            request.put("costing", mode.getProviderProfile());
            request.put("alternates", 2);
            request.put("directions_type", "none");
            request.put("directions_options", new JSONObject().put("units", "kilometers"));
            executePost(parsed, request.toString(), body -> parseRoutes(body), callback);
        } catch (JSONException error) {
            callback.onError("Could not construct the routing request.");
        }
    }

    private interface Parser<T> { T parse(String body) throws JSONException; }

    private <T> void execute(HttpUrl url, Parser<T> parser, ResultCallback<T> callback) {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) {
                callback.onError("Network request failed. Check your connection or provider configuration.");
            }

            @Override public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError("Map service returned HTTP " + response.code() + ".");
                        return;
                    }
                    callback.onSuccess(parser.parse(response.body().string()));
                } catch (Exception error) {
                    callback.onError("The map service returned an unreadable response.");
                }
            }
        });
    }

    private <T> void executePost(HttpUrl url, String json, Parser<T> parser,
                                 ResultCallback<T> callback) {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("X-Client-Id", "github.com/Aniket-Nikam/Safe-Navi")
                .header("Accept", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json; charset=utf-8")))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) {
                callback.onError("Network request failed. Check your connection or provider configuration.");
            }

            @Override public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError("Routing service returned HTTP " + response.code() + ".");
                        return;
                    }
                    callback.onSuccess(parser.parse(response.body().string()));
                } catch (Exception error) {
                    callback.onError("The routing service returned an unreadable response.");
                }
            }
        });
    }

    public static List<PlaceSearchResult> parsePlaces(String body) throws JSONException {
        JSONArray array = new JSONArray(body);
        List<PlaceSearchResult> results = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            results.add(new PlaceSearchResult(item.optString("display_name", "Unnamed place"),
                    Double.parseDouble(item.getString("lat")),
                    Double.parseDouble(item.getString("lon"))));
        }
        return results;
    }

    public static List<RouteCandidate> parseRoutes(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        List<RouteCandidate> results = new ArrayList<>();
        if (!root.has("trip")) {
            throw new JSONException(root.optString("error", "No route was found."));
        }
        addValhallaTrip(results, root.getJSONObject("trip"));
        JSONArray alternates = root.optJSONArray("alternates");
        if (alternates != null) {
            for (int i = 0; i < alternates.length(); i++) {
                JSONObject alternate = alternates.getJSONObject(i);
                if (alternate.has("trip")) addValhallaTrip(results, alternate.getJSONObject("trip"));
            }
        }
        return results;
    }

    private static void addValhallaTrip(List<RouteCandidate> output, JSONObject trip)
            throws JSONException {
        JSONObject summary = trip.getJSONObject("summary");
        JSONArray legs = trip.getJSONArray("legs");
        List<HazardLocation.GeoPoint> geometry = new ArrayList<>();
        for (int i = 0; i < legs.length(); i++) {
            List<HazardLocation.GeoPoint> leg = decodePolyline6(legs.getJSONObject(i).getString("shape"));
            if (!geometry.isEmpty() && !leg.isEmpty()) leg.remove(0);
            geometry.addAll(leg);
        }
        output.add(new RouteCandidate("osm-route-" + (output.size() + 1),
                summary.getDouble("time") / 60.0,
                summary.getDouble("length") * 1000.0, geometry));
    }

    /** Decodes Valhalla's signed polyline format at six decimal places. */
    public static List<HazardLocation.GeoPoint> decodePolyline6(String encoded) {
        List<HazardLocation.GeoPoint> points = new ArrayList<>();
        int index = 0;
        long latitude = 0;
        long longitude = 0;
        while (index < encoded.length()) {
            long[] latResult = decodeValue(encoded, index);
            latitude += latResult[0];
            index = (int) latResult[1];
            long[] lonResult = decodeValue(encoded, index);
            longitude += lonResult[0];
            index = (int) lonResult[1];
            points.add(new HazardLocation.GeoPoint(latitude / 1_000_000.0,
                    longitude / 1_000_000.0));
        }
        return points;
    }

    private static long[] decodeValue(String encoded, int start) {
        long result = 0;
        int shift = 0;
        int index = start;
        int value;
        do {
            value = encoded.charAt(index++) - 63;
            result |= (long) (value & 0x1f) << shift;
            shift += 5;
        } while (value >= 0x20 && index < encoded.length());
        long signed = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
        return new long[]{signed, index};
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
