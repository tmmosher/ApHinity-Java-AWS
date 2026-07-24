package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;

import java.util.List;

/**
 * Application-owned boundary between dashboard conformance analysis and
 * service-calendar corrective-action persistence.
 */
public interface DashboardCorrectiveActionPort {
    List<ServiceEvent> buildPreviewCorrectiveActions(
        Long locationId,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    );

    List<ServiceEvent> persistCorrectiveActions(
        Location location,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    );

    void reconcilePersistedCorrectiveActions(
        Location location,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    );

}
