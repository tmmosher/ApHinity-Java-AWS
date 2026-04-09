package com.aphinity.client_analytics_core.api.core.requests.dashboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for creating a new graph assigned to a location section.
 *
 * @param sectionId target dashboard section identifier when adding to an existing section
 * @param createNewSection when true, the backend creates the next section automatically
 * @param graphType graph template type to create
 */
public record LocationGraphCreateRequest(
    @Positive
    Long sectionId,
    Boolean createNewSection,
    @NotBlank
    @Pattern(regexp = "pie|bar|scatter")
    String graphType
) {
}
