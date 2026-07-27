package com.aphinity.client_analytics_core.api.core.services.location;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read port for resolving graph membership from a dashboard section layout. */
public interface DashboardSectionGraphSelector {
    Optional<SectionGraphSelection> select(Map<String, Object> sectionLayout, Long sectionId);

    record SectionGraphSelection(Long sectionId, List<Long> graphIds) {
        public SectionGraphSelection {
            graphIds = graphIds == null ? List.of() : List.copyOf(graphIds);
        }
    }
}
