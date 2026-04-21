package com.aphinity.client_analytics_core.api.core.services.location.payload;

import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LocationGraphUpdatePayloadValidationFactory {
    private final List<LocationGraphUpdateTraceValidator> validators = List.of(
        new PieGraphPayloadValidator(),
        new IndicatorGraphPayloadValidator(),
        new CartesianGraphPayloadValidator()
    );

    public ValidatedGraphPayload validateForUpdate(
        Object currentData,
        Object nextData,
        Object rawLayout
    ) {
        List<Map<String, Object>> currentTraces = GraphPayloadMapper.toTraceList(currentData);
        String expectedCanonicalType = currentTraces.isEmpty()
            ? null
            : GraphPayloadValidationSupport.resolveExpectedCanonicalType(currentTraces);
        if (expectedCanonicalType != null) {
            resolveValidator(expectedCanonicalType).validate(currentTraces, expectedCanonicalType);
        }

        if (nextData == null) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        List<Map<String, Object>> nextTraces = GraphPayloadMapper.toTraceList(nextData);
        Map<String, Object> normalizedLayout = GraphPayloadValidationSupport.normalizeLayout(rawLayout);

        if (expectedCanonicalType == null) {
            if (nextTraces.isEmpty()) {
                return new ValidatedGraphPayload(List.copyOf(nextTraces), normalizedLayout);
            }
            expectedCanonicalType = GraphPayloadValidationSupport.resolveExpectedCanonicalType(nextTraces);
        } else if (!nextTraces.isEmpty()) {
            String nextCanonicalType = GraphPayloadValidationSupport.resolveExpectedCanonicalType(nextTraces);
            if (!expectedCanonicalType.equals(nextCanonicalType)) {
                throw GraphPayloadValidationSupport.invalidGraphData();
            }
        }

        if (expectedCanonicalType != null) {
            resolveValidator(expectedCanonicalType).validate(nextTraces, expectedCanonicalType);
        }

        return new ValidatedGraphPayload(List.copyOf(nextTraces), normalizedLayout);
    }

    private LocationGraphUpdateTraceValidator resolveValidator(String canonicalTraceType) {
        GraphPayloadFamily family = GraphPayloadValidationSupport.resolveFamily(canonicalTraceType);
        return validators.stream()
            .filter(validator -> validator.supports(family))
            .findFirst()
            .orElseThrow(GraphPayloadValidationSupport::invalidGraphData);
    }

    public record ValidatedGraphPayload(
        List<Map<String, Object>> data,
        Map<String, Object> layout
    ) {
    }
}
