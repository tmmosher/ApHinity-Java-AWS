package com.aphinity.client_analytics_core.api.notifications;

/**
 * Immutable snapshot of a queued mail row used while the row is locked and being processed.
 *
 * @param id outbox row id
 * @param mailType message category
 * @param recipientEmail target email address
 * @param locationName location name associated with the message, if any
 * @param subject email subject
 * @param body email body
 * @param authorizedUserId user id associated with the action that created the message
 * @param attemptCount number of delivery attempts already recorded for the row
 * @param lastError last persisted delivery error, if any
 */
record MailOutboxDeliverySnapshot(
    Long id,
    MailOutboxType mailType,
    String recipientEmail,
    String locationName,
    String subject,
    String body,
    Long authorizedUserId,
    int attemptCount,
    String lastError
) {
}
