package com.aphinity.client_analytics_core.api.core.services.location.payload;

import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.aphinity.client_analytics_core.api.core.services.location.LocationGraphDefinition;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Validates user-submitted graph data updates against the existing graph family
 * and returns normalized Plotly payloads for persistence.
 */
@Component
public class LocationGraphUpdatePayloadValidationFactory {
    private CartesianTraceDateOrderCanonicalizer cartesianTraceDateOrderCanonicalizer;
    private List<LocationGraphUpdateTraceValidator> validators;
    private Map<String, LocationGraphUpdateTraceValidator> validatorsByDefinitionKey = Map.of();

    /**
     * Application composition constructor. Validators are discovered as Spring beans,
     * so a graph module can contribute validation without modifying this factory.
     */
    @Autowired
    public LocationGraphUpdatePayloadValidationFactory(
        CartesianTraceDateOrderCanonicalizer cartesianTraceDateOrderCanonicalizer,
        List<LocationGraphUpdateTraceValidator> validators,
        List<LocationGraphDefinition> definitions
    ) {
        this.cartesianTraceDateOrderCanonicalizer = cartesianTraceDateOrderCanonicalizer;
        this.validators = List.copyOf(validators);
        configureDefinitionValidators(definitions);
    }

    public LocationGraphUpdatePayloadValidationFactory(
        CartesianTraceDateOrderCanonicalizer canonicalizer,
        List<LocationGraphUpdateTraceValidator> discoveredValidators
    ) {
        this(canonicalizer, discoveredValidators, List.of());
    }

    private void configureDefinitionValidators(List<LocationGraphDefinition> definitions) {
        Map<String, LocationGraphUpdateTraceValidator> indexed = new HashMap<>();
        for (LocationGraphDefinition definition : definitions) {
            if (definition.validator() != null) {
                indexed.put(definition.key(), definition.validator());
            }
        }
        this.validatorsByDefinitionKey = Map.copyOf(indexed);
    }

    /**
     * Validates and normalizes a graph update payload.
     *
     * @param currentData current persisted graph data
     * @param nextData proposed replacement graph data
     * @param rawLayout proposed layout payload
     * @return validated data and layout
     */
    public ValidatedGraphPayload validateForUpdate(
        Object currentData,
        Object nextData,
        Object rawLayout
    ) {
        return validateForUpdate(currentData, nextData, rawLayout, null);
    }

    public ValidatedGraphPayload validateForUpdate(
        Object currentData,
        Object nextData,
        Object rawLayout,
        String graphDefinitionKey
    ) {
        List<Map<String, Object>> currentTraces = GraphPayloadMapper.toTraceList(currentData);
        String expectedCanonicalType = currentTraces.isEmpty()
            ? null
            : GraphPayloadValidationSupport.resolveExpectedCanonicalType(currentTraces);
        if (expectedCanonicalType != null) {
            resolveValidator(expectedCanonicalType, graphDefinitionKey).validate(currentTraces, expectedCanonicalType);
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
            resolveValidator(expectedCanonicalType, graphDefinitionKey).validate(nextTraces, expectedCanonicalType);
        }

        if (expectedCanonicalType != null && isCartesianFamily(expectedCanonicalType)) {
            nextTraces = cartesianTraceDateOrderCanonicalizer.canonicalize(nextTraces);
        }

        return new ValidatedGraphPayload(List.copyOf(nextTraces), normalizedLayout);
    }

    private LocationGraphUpdateTraceValidator resolveValidator(String canonicalTraceType) {
        return resolveValidator(canonicalTraceType, null);
    }

    private LocationGraphUpdateTraceValidator resolveValidator(
        String canonicalTraceType,
        String graphDefinitionKey
    ) {
        if (graphDefinitionKey != null) {
            LocationGraphUpdateTraceValidator definitionValidator = validatorsByDefinitionKey.get(graphDefinitionKey);
            if (definitionValidator != null) {
                return definitionValidator;
            }
        }
        GraphPayloadFamily family = GraphPayloadValidationSupport.resolveFamily(canonicalTraceType);
        return validators.stream()
            .filter(validator -> validator.supports(family))
            .findFirst()
            .orElseThrow(GraphPayloadValidationSupport::invalidGraphData);
    }

    private boolean isCartesianFamily(String canonicalTraceType) {
        try {
            return GraphPayloadValidationSupport.resolveFamily(canonicalTraceType) == GraphPayloadFamily.CARTESIAN;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public record ValidatedGraphPayload(
        List<Map<String, Object>> data,
        Map<String, Object> layout
    ) {
    }
}
