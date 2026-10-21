package com.safenavi.app.product;

import com.safenavi.app.safety.model.PlaceSearchResult;

/** Single source of truth for marker and payload. Late suggestions cannot replace an official's choice. */
public final class NewsPinSelection {
    private PlaceSearchResult selected;
    private int revision;
    public int revision() { return revision; }
    public PlaceSearchResult selected() { return selected; }
    public void beginManualChoice() { revision++; }
    public boolean choose(PlaceSearchResult place) {
        if (!valid(place)) return false;
        revision++;
        selected = place;
        return true;
    }
    public boolean suggest(int requestRevision, PlaceSearchResult place) {
        if (requestRevision != revision || !valid(place)) return false;
        selected = place;
        return true;
    }
    public static boolean valid(PlaceSearchResult place) {
        return place != null && Double.isFinite(place.getLatitude()) && Double.isFinite(place.getLongitude())
                && place.getLatitude() >= -90 && place.getLatitude() <= 90
                && place.getLongitude() >= -180 && place.getLongitude() <= 180;
    }
}
