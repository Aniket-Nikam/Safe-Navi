package com.example.xavierproject.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.xavierproject.safety.data.InMemorySafetyRepository;
import com.example.xavierproject.safety.model.CitizenReport;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardHistory;
import com.example.xavierproject.safety.model.HazardLocation;
import com.example.xavierproject.safety.model.HazardLocationType;
import com.example.xavierproject.safety.model.HazardSeverity;
import com.example.xavierproject.safety.model.HazardStatus;
import com.example.xavierproject.safety.model.UserRole;
import com.example.xavierproject.safety.service.AuthorizationException;
import com.example.xavierproject.safety.service.HazardWorkflowService;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class HazardWorkflowServiceTest {
    private InMemorySafetyRepository repository;
    private HazardWorkflowService workflow;
    private CitizenReport report;

    @Before public void setUp() {
        repository = new InMemorySafetyRepository();
        workflow = new HazardWorkflowService(repository);
        report = new CitizenReport();
        report.setId("report-1");
        report.setStatus(HazardStatus.UNDER_REVIEW);
        report.setLocation(new HazardLocation(HazardLocationType.POINT, 19.07, 72.99, "Vashi"));
        repository.saveReport(report);
    }

    @Test public void citizenCannotPublishOfficialHazard() {
        try {
            workflow.verifyReport(report.getId(), draft(), "citizen", UserRole.CITIZEN, 100L);
            fail("Expected authorization failure");
        } catch (AuthorizationException expected) {
            assertTrue(repository.getHazards().isEmpty());
        }
    }

    @Test public void governmentVerificationPublishesHazardAndAuditTrail() {
        Hazard verified = workflow.verifyReport(report.getId(), draft(), "gov-7", UserRole.GOVERNMENT, 100L);
        assertTrue(verified.isGovernmentVerified());
        assertEquals(HazardStatus.ACTIVE, verified.getStatus());
        assertEquals(HazardStatus.VERIFIED, repository.findReport("report-1").getStatus());
        assertEquals(1, repository.getHistory(verified.getId()).size());
    }

    @Test public void rejectionAuditKeepsOriginalStatus() {
        workflow.rejectReport("report-1", "gov-7", "Insufficient evidence", UserRole.GOVERNMENT, 100L);
        List<HazardHistory> history = repository.getHistory("report-1");
        assertEquals(HazardStatus.UNDER_REVIEW, history.get(0).getPreviousStatus());
        assertEquals(HazardStatus.REJECTED, history.get(0).getNewStatus());
    }

    private Hazard draft() {
        Hazard hazard = RuleBasedSafetyRiskEngineTest.hazard("Flooding", 19.07, 72.99,
                HazardSeverity.HIGH, HazardStatus.REPORTED, false);
        hazard.setReason("Photo and site inspection");
        return hazard;
    }
}
