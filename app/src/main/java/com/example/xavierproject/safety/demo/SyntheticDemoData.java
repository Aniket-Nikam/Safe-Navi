package com.example.xavierproject.safety.demo;

import com.example.xavierproject.safety.data.InMemorySafetyRepository;
import com.example.xavierproject.safety.model.CitizenReport;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardLocation;
import com.example.xavierproject.safety.model.HazardLocationType;
import com.example.xavierproject.safety.model.HazardSeverity;
import com.example.xavierproject.safety.model.HazardStatus;

public final class SyntheticDemoData {
    private SyntheticDemoData() {
    }

    public static InMemorySafetyRepository create(long now) {
        InMemorySafetyRepository repository = new InMemorySafetyRepository();
        repository.saveHazard(hazard("demo-flooding", "Heavy waterlogging after rainfall",
                "FLOODING", HazardSeverity.HIGH, HazardStatus.ACTIVE,
                19.0657, 72.9986, "Palm Beach Road, Vashi", 3, 2, now));
        repository.saveHazard(hazard("demo-building", "Unsafe abandoned structure",
                "UNSAFE_BUILDING", HazardSeverity.CRITICAL, HazardStatus.MONITORING,
                19.1136, 72.8697, "Near Andheri Station", 2, 4, now - 12 * 3_600_000L));
        repository.saveHazard(hazard("demo-lighting", "Poor street lighting",
                "POOR_LIGHTING", HazardSeverity.MODERATE, HazardStatus.ACTIVE,
                19.0185, 73.0390, "CBD Belapur road segment", 5, 6, now - 8 * 3_600_000L));

        CitizenReport report = new CitizenReport();
        report.setId("demo-report-flooding");
        report.setCitizenId("demo-citizen");
        report.setTitle("Road heavily flooded after rainfall");
        report.setCategoryKey("FLOODING");
        report.setDescription("Waterlogging is blocking one side of the road.");
        report.setLocation(new HazardLocation(HazardLocationType.ROAD_SEGMENT,
                19.0425, 72.8636, "Sion Circle service road"));
        report.setStatus(HazardStatus.UNDER_REVIEW);
        report.setConfirmationCount(4);
        report.setSynthetic(true);
        report.setCreatedAt(now - 30 * 60_000L);
        report.setUpdatedAt(now - 10 * 60_000L);
        repository.saveReport(report);
        return repository;
    }

    private static Hazard hazard(String id, String title, String category, HazardSeverity severity,
                                 HazardStatus status, double latitude, double longitude,
                                 String locationName, int reports, int recurrence, long updatedAt) {
        Hazard hazard = new Hazard();
        hazard.setId(id);
        hazard.setTitle(title);
        hazard.setCategoryKey(category);
        hazard.setDescription(title);
        hazard.setReason(title);
        hazard.setLocation(new HazardLocation(HazardLocationType.POINT, latitude, longitude, locationName));
        hazard.setSeverity(severity);
        hazard.setStatus(status);
        hazard.setGovernmentVerified(true);
        hazard.setSynthetic(true);
        hazard.setSource("SYNTHETIC_GOVERNMENT_DEMO");
        hazard.setGovernmentEmployeeId("demo-government");
        hazard.setLinkedReportCount(reports);
        hazard.setHistoricalRecurrence(recurrence);
        hazard.setCreatedAt(updatedAt - 3_600_000L);
        hazard.setUpdatedAt(updatedAt);
        return hazard;
    }
}
