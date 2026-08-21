package com.safenavi.app.safety.data;

import com.safenavi.app.safety.model.CitizenReport;
import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.HazardHistory;
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
