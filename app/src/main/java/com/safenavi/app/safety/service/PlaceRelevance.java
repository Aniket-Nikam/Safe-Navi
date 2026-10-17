package com.safenavi.app.safety.service;

import com.safenavi.app.safety.model.PlaceSearchResult;
import java.util.*;

/** Text relevance first, proximity second. Do not mistake a campus parking lot for the college. */
public final class PlaceRelevance {
    private PlaceRelevance() { }
    private static String normalized(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim(); }
    public static List<PlaceSearchResult> rank(String query, List<PlaceSearchResult> places, double lat, double lon) {
        String text=normalized(query);
        List<PlaceSearchResult> ranked=new ArrayList<>();
        for(PlaceSearchResult place:places) {
            String address=normalized(place.getDisplayName()); boolean match=text.isEmpty();
            for(String word:text.split(" +")) if(!word.isEmpty() && address.contains(word)) match=true;
            if(match) ranked.add(place);
        }
        ranked.sort(Comparator.comparingDouble((PlaceSearchResult place) -> -score(text,place,lat,lon)));
        return ranked;
    }
    private static double score(String text, PlaceSearchResult place, double lat, double lon) {
        String name=normalized(place.getPrimaryText()), address=normalized(place.getDisplayName());
        double score=name.equals(text)?250:name.startsWith(text)?120:name.contains(text)?90:0;
        for(String word:text.split(" +")) if(!word.isEmpty()) score+=name.contains(word)?40:address.contains(word)?8:0;
        for(String amenity:new String[]{"parking","canteen","toilet","hostel","bus stop"})
            if(name.contains(amenity) && !text.contains(amenity)) score-=100;
        double km=Math.hypot((place.getLatitude()-lat)*111,(place.getLongitude()-lon)*105);
        return score-Math.min(35,km*.5);
    }
}
