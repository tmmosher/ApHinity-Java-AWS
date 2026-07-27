package com.aphinity.client_analytics_core.api.core.services.location;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolves section graph order from the persisted JSON dashboard layout contract. */
@Component
public final class JsonDashboardSectionGraphSelector implements DashboardSectionGraphSelector {
    private static final String SECTIONS_FIELD = "sections";
    private static final String SECTION_ID_FIELD = "section_id";
    private static final String GRAPH_IDS_FIELD = "graph_ids";

    @Override
    public Optional<SectionGraphSelection> select(Map<String, Object> sectionLayout, Long sectionId) {
        if (sectionId == null || sectionLayout == null) {
            return Optional.empty();
        }
        Object rawSections = sectionLayout.get(SECTIONS_FIELD);
        if (!(rawSections instanceof List<?> sections)) {
            return Optional.empty();
        }
        for (Object rawSection : sections) {
            if (!(rawSection instanceof Map<?, ?> section)
                || !matchesId(section.get(SECTION_ID_FIELD), sectionId)) {
                continue;
            }
            return Optional.of(new SectionGraphSelection(sectionId, graphIds(section.get(GRAPH_IDS_FIELD))));
        }
        return Optional.empty();
    }

    private List<Long> graphIds(Object rawGraphIds) {
        if (!(rawGraphIds instanceof List<?> values)) {
            return List.of();
        }
        Set<Long> orderedIds = new LinkedHashSet<>();
        for (Object value : values) {
            if (value instanceof Number number && number.longValue() > 0L) {
                orderedIds.add(number.longValue());
            }
        }
        return List.copyOf(new ArrayList<>(orderedIds));
    }

    private boolean matchesId(Object rawId, Long expectedId) {
        return rawId instanceof Number number && number.longValue() == expectedId;
    }
}
