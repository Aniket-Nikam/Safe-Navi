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

/** Provider boundary for OpenStreetMap-backed search and routing services. */
public class OpenMapService {
    private static final String USER_AGENT = "Safe-Navi/1.0 (github.com/Aniket-Nikam/Safe-Navi)";
    private static final long NOMINATIM_INTERVAL_MS = 1_100L;
    private final OkHttpClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastSearchAt;
    private final java.util.LinkedHashMap<String, List<PlaceSearchResult>> suggestionCache = new java.util.LinkedHashMap<>();

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
                .addQueryParameter("viewbox", "72.65,19.55,73.35,18.85")
                .addQueryParameter("bounded", "1")
                .addQueryParameter("accept-language", "en")
                .build();
        execute(url, OpenMapService::parsePlaces, new ResultCallback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> values) {
                List<PlaceSearchResult> matching = filterSearchResults(query, values);
                if (matching.isEmpty()) searchFuzzy(query, callback); else callback.onSuccess(matching);
            }
            @Override public void onError(String message) { searchFuzzy(query, callback); }
        });
    }

    private void searchFuzzy(String query, ResultCallback<List<PlaceSearchResult>> callback) {
        HttpUrl url = HttpUrl.parse("https://photon.komoot.io/api/").newBuilder()
                .addQueryParameter("q", query.replace(',', ' ')).addQueryParameter("limit", "8")
                .addQueryParameter("bbox", "72.65,18.85,73.35,19.55")
                .addQueryParameter("lat", "19.043").addQueryParameter("lon", "73.023")
                .build();
        execute(url, OpenMapService::parsePhotonPlaces, new ResultCallback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> values) {
                callback.onSuccess(filterSearchResults(query, values));
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    /** Photon supports search-as-you-type; public Nominatim MUST remain explicit-submit only. */
    public Call suggest(String query, double latitude, double longitude, ResultCallback<List<PlaceSearchResult>> callback) {
        String cacheKey = query.trim().toLowerCase(java.util.Locale.ROOT);
        synchronized (suggestionCache) {
            if (suggestionCache.containsKey(cacheKey)) {
                callback.onSuccess(new ArrayList<>(suggestionCache.get(cacheKey))); return null;
            }
        }
        HttpUrl url = HttpUrl.parse("https://photon.komoot.io/api/").newBuilder()
                .addQueryParameter("q", query.replace(',', ' ')).addQueryParameter("limit", "8")
                .addQueryParameter("bbox", "72.65,18.85,73.35,19.55")
                .addQueryParameter("lat", Double.toString(Math.max(18.85, Math.min(19.55, latitude))))
                .addQueryParameter("lon", Double.toString(Math.max(72.65, Math.min(73.35, longitude))))
                .addQueryParameter("lang", "en").build();
        return execute(url, OpenMapService::parsePhotonPlaces, new ResultCallback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> values) {
                List<PlaceSearchResult> matches = uniquePlaces(filterSearchResults(query, values));
                synchronized (suggestionCache) {
                    if (suggestionCache.size() >= 32) suggestionCache.remove(suggestionCache.keySet().iterator().next());
                    suggestionCache.put(cacheKey, new ArrayList<>(matches));
                }
                callback.onSuccess(matches);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public static List<PlaceSearchResult> uniquePlaces(List<PlaceSearchResult> values) {
        List<PlaceSearchResult> output = new ArrayList<>();
        for (PlaceSearchResult value : values) {
            boolean duplicate = false;
            for (PlaceSearchResult existing : output) {
                if (existing.getPrimaryText().equalsIgnoreCase(value.getPrimaryText())
                        && Math.abs(existing.getLatitude() - value.getLatitude()) < .002
                        && Math.abs(existing.getLongitude() - value.getLongitude()) < .002) { duplicate = true; break; }
            }
            if (!duplicate) output.add(value);
        }
        return output;
    }

    public static List<PlaceSearchResult> filterSearchResults(String query, List<PlaceSearchResult> values) {
        List<PlaceSearchResult> results = new ArrayList<>();
        String[] components = query.toLowerCase(java.util.Locale.ROOT).split(",");
        for (PlaceSearchResult place : values) {
            if (place.getLatitude() < 18.85 || place.getLatitude() > 19.55
                    || place.getLongitude() < 72.65 || place.getLongitude() > 73.35) continue;
            String address = place.getDisplayName().toLowerCase(java.util.Locale.ROOT);
            boolean matches = true;
            for (int i = 1; i < components.length; i++) {
                String locality = components[i].trim();
                if (!locality.isEmpty() && !address.contains(locality)) { matches = false; break; }
            }
            if (matches) results.add(place);
        }
        return results;
    }

    public static List<PlaceSearchResult> parsePhotonPlaces(String body) throws JSONException {
        JSONArray features = new JSONObject(body).getJSONArray("features");
        List<PlaceSearchResult> results = new ArrayList<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");
            JSONObject properties = feature.getJSONObject("properties");
            double lon = coordinates.getDouble(0), lat = coordinates.getDouble(1);
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) continue;
            List<String> parts = new ArrayList<>();
            for (String key : new String[]{"name", "street", "district", "city", "state", "country"}) {
                String value = properties.optString(key, "").trim();
                if (!value.isEmpty() && !parts.contains(value)) parts.add(value);
            }
            results.add(new PlaceSearchResult(parts.isEmpty() ? "Unnamed place" : String.join(", ", parts), lat, lon));
        }
        return results;
    }

    public synchronized void reverse(double latitude, double longitude, ResultCallback<String> callback) {
        long now = System.currentTimeMillis();
        long delay = Math.max(0, NOMINATIM_INTERVAL_MS - (now - lastSearchAt));
        lastSearchAt = now + delay;
        handler.postDelayed(() -> {
            HttpUrl base = HttpUrl.parse(BuildConfig.NOMINATIM_BASE_URL);
            if (base == null) { callback.onError("Invalid geocoding service configuration."); return; }
            HttpUrl url = base.newBuilder().addPathSegment("reverse")
                    .addQueryParameter("lat", String.valueOf(latitude))
                    .addQueryParameter("lon", String.valueOf(longitude))
                    .addQueryParameter("format", "jsonv2")
                    .addQueryParameter("zoom", "18")
                    .addQueryParameter("accept-language", "en")
                    .build();
            execute(url, OpenMapService::parseReverse, callback);
        }, delay);
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

    private <T> Call execute(HttpUrl url, Parser<T> parser, ResultCallback<T> callback) {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
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
        return call;
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

    public static String parseReverse(String body) throws JSONException {
        JSONObject item = new JSONObject(body);
        String displayName = item.optString("display_name").trim();
        if (displayName.isEmpty()) throw new JSONException("No address was returned.");
        return displayName;
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
