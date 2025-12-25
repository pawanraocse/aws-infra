package com.learning.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * SES-based email service for production use.
 * Only active in 'prod' profile. Uses DevEmailService in dev/test.
 */
@Service
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SesEmailService implements EmailService {

    private final SesClient sesClient;

    @Value("${aws.ses.from-email:noreply@example.com}")
    private String fromEmail;

    @Override
    public void sendInvitationEmail(String to, String inviteLink, String orgName) {
        try {
            String subject = "You've been invited to join " + orgName;
            String bodyHtml = String.format("""
                    <html>
                    <body>
                        <h2>Welcome!</h2>
                        <p>You have been invited to join <strong>%s</strong>.</p>
                        <p>Click the link below to accept the invitation:</p>
                        <p><a href="%s">Join %s</a></p>
                        <p>This link will expire in 48 hours.</p>
                    </body>
                    </html>
                    """, orgName, inviteLink, orgName);

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(fromEmail)
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).build())
                            .body(Body.builder()
                                    .html(Content.builder().data(bodyHtml).build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
            log.info("Invitation email sent via SES to {} from {}", to, fromEmail);
        } catch (SesException e) {
            log.error("Failed to send invitation email to {} via SES: {}", to, e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to send email via SES", e);
        }
    }
}
