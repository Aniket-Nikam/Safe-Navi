package com.example.xavierproject.safety.data;

import com.example.xavierproject.safety.model.CitizenReport;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardHistory;
import java.util.List;

public interface SafetyRepository {
    List<Hazard> getHazards();
    List<CitizenReport> getReports();
    List<HazardHistory> getHistory(String hazardId);
    Hazard findHazard(String hazardId);
    CitizenReport findReport(String reportId);
    void saveHazard(Hazard hazard);
    void saveReport(CitizenReport report);
    void appendHistory(HazardHistory history);
}
