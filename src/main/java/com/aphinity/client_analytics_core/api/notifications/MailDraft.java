package com.aphinity.client_analytics_core.api.notifications;

/**
 * Plain-text mail content that can be persisted, retried, and sent.
 *
 * @param subject email subject line
 * @param body email body
 */
public record MailDraft(String subject, String body) {
}
