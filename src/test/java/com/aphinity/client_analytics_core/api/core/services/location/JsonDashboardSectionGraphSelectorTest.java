package com.aphinity.client_analytics_core.api.core.services.location;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDashboardSectionGraphSelectorTest {
    private final JsonDashboardSectionGraphSelector selector = new JsonDashboardSectionGraphSelector();

    @Test
    void selectsGraphIdsInLayoutOrderWithoutDuplicates() {
        Map<String, Object> layout = Map.of("sections", List.of(
            Map.of("section_id", 1, "graph_ids", List.of(10, 20)),
            Map.of("section_id", 2, "graph_ids", List.of(31, 12, 31, -1, "invalid"))
        ));

        DashboardSectionGraphSelector.SectionGraphSelection selection = selector.select(layout, 2L).orElseThrow();

        assertEquals(2L, selection.sectionId());
        assertEquals(List.of(31L, 12L), selection.graphIds());
    }

    @Test
    void distinguishesAValidEmptySectionFromAMissingSection() {
        Map<String, Object> layout = Map.of("sections", List.of(
            Map.of("section_id", 2, "graph_ids", List.of())
        ));

        assertEquals(List.of(), selector.select(layout, 2L).orElseThrow().graphIds());
        assertTrue(selector.select(layout, 3L).isEmpty());
    }
}
