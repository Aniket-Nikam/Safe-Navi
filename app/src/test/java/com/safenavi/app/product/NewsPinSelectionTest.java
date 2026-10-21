package com.safenavi.app.product;

import com.safenavi.app.safety.model.PlaceSearchResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class NewsPinSelectionTest {
    private PlaceSearchResult place(String label, double lat) { return new PlaceSearchResult(label, lat, 72.83); }
    @Test public void suggestionBecomesThePublishSelectionWithoutAManualTap() {
        NewsPinSelection state = new NewsPinSelection();
        PlaceSearchResult suggestion = place("Juhu", 19.1);
        assertTrue(state.suggest(state.revision(), suggestion));
        assertSame(suggestion, state.selected());
    }
    @Test public void lateLookupCannotReplaceManualRoadPin() {
        NewsPinSelection state = new NewsPinSelection(); int request = state.revision();
        PlaceSearchResult manual = place("Verified road", 19.107281);
        assertTrue(state.choose(manual));
        assertFalse(state.suggest(request, place("Juhu", 19.1)));
        assertSame(manual, state.selected());
    }
    @Test public void openingChangeLocationCancelsPendingSuggestionWithoutLosingPin() {
        NewsPinSelection state = new NewsPinSelection();
        PlaceSearchResult previous = place("Previous choice", 19.1); state.choose(previous);
        int request = state.revision(); state.beginManualChoice();
        assertFalse(state.suggest(request, place("Late", 19.2))); assertSame(previous, state.selected());
    }
    @Test public void invalidCoordinatesAreNeverAccepted() {
        NewsPinSelection state = new NewsPinSelection();
        assertFalse(state.choose(place("NaN", Double.NaN)));
        assertFalse(state.choose(new PlaceSearchResult("Outside", 19, 200)));
        assertFalse(state.suggest(0, place("Infinity", Double.POSITIVE_INFINITY)));
        assertNull(state.selected());
    }
}
