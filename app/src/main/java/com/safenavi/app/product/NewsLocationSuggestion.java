package com.safenavi.app.product;

import com.safenavi.app.safety.model.PlaceSearchResult;
import java.util.*;
import java.util.regex.Pattern;

/** Area hints only, never incident coordinates. Coordinates must come from the map provider. */
public final class NewsLocationSuggestion {
    private NewsLocationSuggestion() { }
    // Supported search region matches OpenMapService; do not turn an unrelated city into a Mumbai pin.
    private static final String[] AREAS = {
        "CBD Belapur", "Kopar Khairane", "Koparkhairane", "Ghansoli", "Sanpada", "Juinagar",
        "Kharghar", "Kamothe", "Kalamboli", "Taloja", "Panvel", "Belapur", "Nerul", "Vashi", "Airoli",
        "Bhandup", "Mulund", "Nahur", "Powai", "Vikhroli", "Ghatkopar", "Chembur", "Govandi",
        "Mankhurd", "Kurla", "Sion", "Wadala", "Matunga", "Dadar", "Mahim", "Bandra", "Khar",
        "Santacruz", "Vile Parle", "Juhu", "Andheri", "Versova", "Jogeshwari", "Goregaon",
        "Malad", "Kandivali", "Borivali", "Dahisar", "Worli", "Prabhadevi", "Parel", "Byculla",
        "Mazgaon", "Colaba", "Churchgate", "Marine Drive", "Bhiwandi", "Mumbra", "Kalwa"
    };

    private static List<String> areas(String text) {
        String value = text == null ? "" : text;
        List<String> matches = new ArrayList<>();
        for (String area : AREAS) {
            if (Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(area) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE).matcher(value).find()) {
                boolean contained = false;
                for (String found : matches) if (found.toLowerCase(Locale.ROOT).contains(area.toLowerCase(Locale.ROOT))) contained = true;
                if (!contained) matches.add(area);
            }
        }
        return matches;
    }

    public static String query(String headline, String summary, String localityHint) {
        List<String> titleAreas = areas(headline);
        if (titleAreas.size() > 1) return ""; // A story mentioning several areas needs an official's choice.
        if (titleAreas.size() == 1) return titleAreas.get(0) + ", Mumbai";
        List<String> hints = areas(localityHint);
        List<String> summaryAreas = areas(summary);
        if (hints.size() > 1 || summaryAreas.size() > 1) return "";
        if (hints.size() == 1) {
            if (!summaryAreas.isEmpty() && !summaryAreas.get(0).equals(hints.get(0))) return "";
            return hints.get(0) + ", Mumbai";
        }
        return summaryAreas.size() == 1 ? summaryAreas.get(0) + ", Mumbai" : "";
    }

    /** Prefer an actual area result, not the first cafe/shop whose name merely contains the query. */
    public static PlaceSearchResult bestMatch(String query, List<PlaceSearchResult> places) {
        if (query == null || query.isEmpty() || places == null) return null;
        String area = query.split(",")[0].trim();
        for (PlaceSearchResult place : places) {
            if (place == null || !NewsPinSelection.valid(place)) continue;
            if (place.getLatitude() < 18.85 || place.getLatitude() > 19.55
                    || place.getLongitude() < 72.65 || place.getLongitude() > 73.35) continue;
            String name = place.getPrimaryText().trim();
            if (name.equalsIgnoreCase(area) || name.equalsIgnoreCase(area + " East")
                    || name.equalsIgnoreCase(area + " West")) return place;
        }
        return null;
    }
}
