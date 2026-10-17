package com.safenavi.app.safety;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.service.PlaceRelevance;
public class PlaceRelevanceTest {
    @Test public void campusOutranksParkingUnlessParkingIsRequested() {
        List<PlaceSearchResult> places=Arrays.asList(new PlaceSearchResult("SIES College Parking, Nerul",19.04,73.02),new PlaceSearchResult("SIES College, Nerul",19.042,73.023));
        assertEquals("SIES College",PlaceRelevance.rank("SIES",places,19.04,73.02).get(0).getPrimaryText());
        assertEquals("SIES College Parking",PlaceRelevance.rank("SIES parking",places,19.04,73.02).get(0).getPrimaryText());
    }
    @Test public void unrelatedResultsAreNotAutoSelected() {
        assertTrue(PlaceRelevance.rank("zzzz",Collections.singletonList(new PlaceSearchResult("Hospital, Mumbai",19.1,72.9)),19,73).isEmpty());
    }
}
