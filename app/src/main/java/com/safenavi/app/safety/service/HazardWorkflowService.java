package com.safenavi.app.safety.service;

import com.safenavi.app.safety.data.SafetyRepository;
import com.safenavi.app.safety.model.CitizenReport;
import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.HazardHistory;
import com.safenavi.app.safety.model.HazardSeverity;
import com.safenavi.app.safety.model.HazardStatus;
import com.safenavi.app.safety.model.UserRole;
import java.util.UUID;

public class HazardWorkflowService {
    private final SafetyRepository repository;
    private final RoleAuthorizer authorizer;

    public HazardWorkflowService(SafetyRepository repository) {
        this(repository, new RoleAuthorizer());
    }

    public HazardWorkflowService(SafetyRepository repository, RoleAuthorizer authorizer) {
        this.repository = repository;
        this.authorizer = authorizer;
    }

    public Hazard verifyReport(String reportId, Hazard draft, String employeeId, UserRole role, long now) {
        authorizer.requireGovernment(role);
        CitizenReport report = requireReport(reportId);
        if (draft == null || draft.getLocation() == null || draft.getSeverity() == null
                || draft.getCategoryKey() == null || draft.getReason() == null
                || draft.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("A verified hazard requires location, category, severity, and reason.");
        }

        draft.setId(draft.getId() == null ? "hazard-" + UUID.randomUUID() : draft.getId());
        draft.setGovernmentVerified(true);
        draft.setGovernmentEmployeeId(employeeId);
        draft.setSource("GOVERNMENT_VERIFIED_REPORT");
        draft.setStatus(draft.getStatus() == null || draft.getStatus() == HazardStatus.REPORTED
                ? HazardStatus.ACTIVE : draft.getStatus());
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draft.setLinkedReportCount(Math.max(1, draft.getLinkedReportCount()));
        repository.saveHazard(draft);

        HazardStatus previous = report.getStatus();
        report.setStatus(HazardStatus.VERIFIED);
        report.setLinkedHazardId(draft.getId());
        report.setUpdatedAt(now);
        repository.saveReport(report);
        appendHistory(draft, null, draft.getStatus(), null, draft.getSeverity(),
                employeeId, "REPORT_VERIFIED", "Verified report " + reportId, now);
        return draft;
    }

    public void rejectReport(String reportId, String employeeId, String reason, UserRole role, long now) {
        authorizer.requireGovernment(role);
        CitizenReport report = requireReport(reportId);
        HazardStatus previousStatus = report.getStatus();
        report.setStatus(HazardStatus.REJECTED);
        report.setUpdatedAt(now);
        repository.saveReport(report);
        appendHistory(null, previousStatus, HazardStatus.REJECTED, null, null,
                employeeId, "REPORT_REJECTED", reason, now, reportId);
    }

    public void markDuplicate(String reportId, String hazardId, String employeeId, UserRole role, long now) {
        authorizer.requireGovernment(role);
        CitizenReport report = requireReport(reportId);
        Hazard hazard = requireHazard(hazardId);
        report.setStatus(HazardStatus.DUPLICATE);
        report.setLinkedHazardId(hazardId);
        report.setUpdatedAt(now);
        repository.saveReport(report);
        hazard.setLinkedReportCount(hazard.getLinkedReportCount() + 1);
        hazard.setUpdatedAt(now);
        repository.saveHazard(hazard);
        appendHistory(hazard, hazard.getStatus(), hazard.getStatus(), hazard.getSeverity(),
                hazard.getSeverity(), employeeId, "REPORT_LINKED_AS_DUPLICATE",
                "Linked report " + reportId, now);
    }

    public void updateHazard(String hazardId, HazardSeverity severity, HazardStatus status,
                             String employeeId, String reason, UserRole role, long now) {
        authorizer.requireGovernment(role);
        Hazard hazard = requireHazard(hazardId);
        validateTransition(hazard.getStatus(), status);
        HazardStatus previousStatus = hazard.getStatus();
        HazardSeverity previousSeverity = hazard.getSeverity();
        hazard.setStatus(status);
        hazard.setSeverity(severity);
        hazard.setUpdatedAt(now);
        if (status == HazardStatus.RESOLVED) hazard.setResolvedAt(now);
        repository.saveHazard(hazard);
        appendHistory(hazard, previousStatus, status, previousSeverity, severity,
                employeeId, "HAZARD_UPDATED", reason, now);
    }

    private void validateTransition(HazardStatus from, HazardStatus to) {
        if (to == null) throw new IllegalArgumentException("A status is required.");
        if (from == HazardStatus.CLOSED) {
            throw new IllegalStateException("A closed hazard cannot be modified.");
        }
        if (to == HazardStatus.REJECTED || to == HazardStatus.DUPLICATE) {
            throw new IllegalStateException("Official hazards cannot transition to report-only outcomes.");
        }
    }

    private CitizenReport requireReport(String id) {
        CitizenReport report = repository.findReport(id);
        if (report == null) throw new IllegalArgumentException("Report not found: " + id);
        return report;
    }

    private Hazard requireHazard(String id) {
        Hazard hazard = repository.findHazard(id);
        if (hazard == null) throw new IllegalArgumentException("Hazard not found: " + id);
        return hazard;
    }

    private void appendHistory(Hazard hazard, HazardStatus previousStatus, HazardStatus newStatus,
                               HazardSeverity previousSeverity, HazardSeverity newSeverity,
                               String employeeId, String action, String reason, long now) {
        appendHistory(hazard, previousStatus, newStatus, previousSeverity, newSeverity,
                employeeId, action, reason, now, hazard == null ? null : hazard.getId());
    }

    private void appendHistory(Hazard hazard, HazardStatus previousStatus, HazardStatus newStatus,
                               HazardSeverity previousSeverity, HazardSeverity newSeverity,
                               String employeeId, String action, String reason, long now, String entityId) {
        HazardHistory item = new HazardHistory();
        item.setId("history-" + UUID.randomUUID());
        item.setHazardId(entityId);
        item.setPreviousStatus(previousStatus);
        item.setNewStatus(newStatus);
        item.setPreviousSeverity(previousSeverity);
        item.setNewSeverity(newSeverity);
        item.setEmployeeId(employeeId);
        item.setAction(action);
        item.setReason(reason);
        item.setTimestamp(now);
        repository.appendHistory(item);
    }
}
