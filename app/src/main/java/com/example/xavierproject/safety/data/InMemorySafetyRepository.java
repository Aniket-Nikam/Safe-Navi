package com.example.xavierproject.safety.data;

import com.example.xavierproject.safety.model.CitizenReport;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardHistory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemorySafetyRepository implements SafetyRepository {
    private final Map<String, Hazard> hazards = new LinkedHashMap<>();
    private final Map<String, CitizenReport> reports = new LinkedHashMap<>();
    private final List<HazardHistory> history = new ArrayList<>();

    @Override public synchronized List<Hazard> getHazards() { return new ArrayList<>(hazards.values()); }
    @Override public synchronized List<CitizenReport> getReports() { return new ArrayList<>(reports.values()); }
    @Override public synchronized Hazard findHazard(String id) { return hazards.get(id); }
    @Override public synchronized CitizenReport findReport(String id) { return reports.get(id); }
    @Override public synchronized void saveHazard(Hazard hazard) { hazards.put(hazard.getId(), hazard); }
    @Override public synchronized void saveReport(CitizenReport report) { reports.put(report.getId(), report); }
    @Override public synchronized void appendHistory(HazardHistory item) { history.add(item); }

    @Override
    public synchronized List<HazardHistory> getHistory(String hazardId) {
        List<HazardHistory> result = new ArrayList<>();
        for (HazardHistory item : history) {
            if (hazardId == null || hazardId.equals(item.getHazardId())) result.add(item);
        }
        return result;
    }
}
