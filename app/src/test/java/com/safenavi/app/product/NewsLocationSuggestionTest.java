package com.safenavi.app.product;

import com.safenavi.app.safety.model.PlaceSearchResult;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NewsLocationSuggestionTest {
    @Test public void headlineJuhuBeatsOldCityOnlyHint() {
        assertEquals("Juhu, Mumbai", NewsLocationSuggestion.query("Police investigate the Juhu attack", "Mumbai police are investigating.", "Mumbai"));
    }
    @Test public void noArbitraryMumbaiCentreForCityOnlyStory() {
        assertEquals("", NewsLocationSuggestion.query("Mumbai news update", "", "Mumbai"));
    }
    @Test public void unsupportedCityIsNotSubstitutedWithMumbai() {
        assertEquals("", NewsLocationSuggestion.query("Police arrest suspects in Jaipur", "", "Mumbai"));
    }
    @Test public void mostSpecificAreaDoesNotDoubleCountBelapur() {
        assertEquals("CBD Belapur, Mumbai", NewsLocationSuggestion.query("Road work in CBD Belapur", "", "Mumbai"));
    }
    @Test public void severalAreasNeedAnOfficialChoice() {
        assertEquals("", NewsLocationSuggestion.query("Flooding in Vashi and Nerul", "", "Mumbai"));
    }
    @Test public void areaInSummaryCanBeUsedWhenTitleIsGeneric() {
        assertEquals("Vashi, Mumbai", NewsLocationSuggestion.query("Traffic update", "Road work in Vashi", "Mumbai"));
    }
    @Test public void conflictingHintsDoNotChooseTheFirstOne() {
        assertEquals("", NewsLocationSuggestion.query("Traffic update", "Road work in Juhu", "Vashi"));
    }
    @Test public void wordBoundariesPreventNameSubstringMatches() {
        assertEquals("Kharghar, Mumbai", NewsLocationSuggestion.query("Kharghar roads", "", ""));
        assertEquals("", NewsLocationSuggestion.query("A Parelkar interview", "", ""));
    }
    @Test public void actualAreaPreferredToCafeAndOutsideRegion() {
        PlaceSearchResult area = new PlaceSearchResult("Juhu, Mumbai, India", 19.1, 72.83);
        assertSame(area, NewsLocationSuggestion.bestMatch("Juhu, Mumbai", Arrays.asList(
                new PlaceSearchResult("Juhu Cafe, Mumbai", 19.1, 72.83),
                new PlaceSearchResult("Juhu, Elsewhere", 50, 30), area)));
    }
    @Test public void noMatchingAreaReturnsNullRatherThanUnrelatedPlace() {
        assertNull(NewsLocationSuggestion.bestMatch("Juhu, Mumbai", Collections.singletonList(
                new PlaceSearchResult("Juhu Cafe, Mumbai", 19.1, 72.83))));
    }
}
