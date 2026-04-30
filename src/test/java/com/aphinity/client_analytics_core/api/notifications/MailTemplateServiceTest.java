package com.aphinity.client_analytics_core.api.notifications;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailTemplateServiceTest {
    private final MailTemplateService mailTemplateService = new MailTemplateService();

    @Test
    void buildWorkOrderDraftNormalizesWhitespaceAndComposesExpectedBody() {
        MailDraft draft = mailTemplateService.buildWorkOrderDraft(
            "  Austin  ",
            "  Generator alarm  ",
            "  Inspect the west valve  "
        );

        assertEquals("Work order for Austin: Generator alarm", draft.subject());
        assertEquals(
            "A corrective action work order has been created.\n\n"
                + "Location: Austin\n"
                + "Event title: Generator alarm\n"
                + "Description: Inspect the west valve\n",
            draft.body()
        );
    }

    @Test
    void buildWorkOrderDraftUsesFallbacksForBlankFields() {
        MailDraft draft = mailTemplateService.buildWorkOrderDraft("   ", null, "   ");

        assertEquals("Work order for Unknown location: Corrective action", draft.subject());
        assertTrue(draft.body().contains("Location: Unknown location"));
        assertTrue(draft.body().contains("Event title: Corrective action"));
        assertTrue(draft.body().contains("Description: No description provided."));
    }
}
