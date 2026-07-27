package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguredDashboardGraphCapabilityQueryTest {
    @Test
    void currentConfigurationOverridesMissingPersistedCapabilityMetadata() {
        LocationDashboardImportStrategy strategy = mock(LocationDashboardImportStrategy.class);
        LocationDashboardImportStrategyConfig.DerivedGraphConfig definition =
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "recent-sample-measurements",
                "Recent Sample Measurements",
                null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.RECENT_SAMPLE_MEASUREMENTS,
                "table",
                List.of(),
                false
            );
        when(strategy.graphDefinitions()).thenReturn(List.of());
        when(strategy.derivedGraphDefinitions()).thenReturn(List.of(definition));
        Graph graph = new Graph();
        graph.setId(7L);
        graph.setName("Recent Sample Measurements");
        graph.setLayout(Map.of());

        ConfiguredDashboardGraphCapabilityQuery query = new ConfiguredDashboardGraphCapabilityQuery(
            ignored -> Optional.of(strategy),
            new LocationDashboardGraphMatcher()
        );

        assertEquals(Map.of(7L, false), query.resolveSectionTimeRangeCapabilities("Hoag Hospital", List.of(graph)));
    }

    @Test
    void unconfiguredGraphsRemainEnabledByDefault() {
        Graph graph = new Graph();
        graph.setId(8L);
        ConfiguredDashboardGraphCapabilityQuery query = new ConfiguredDashboardGraphCapabilityQuery(
            ignored -> Optional.empty(),
            new LocationDashboardGraphMatcher()
        );

        assertEquals(Map.of(8L, true), query.resolveSectionTimeRangeCapabilities("Custom", List.of(graph)));
    }
}
