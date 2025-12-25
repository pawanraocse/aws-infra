package com.learning.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mock email service for development and testing.
 * Logs emails to console instead of actually sending them.
 * Active in all profiles EXCEPT 'prod'.
 */
@Service
@Profile("!prod")
@Slf4j
public class DevEmailService implements EmailService {

    @Override
    public void sendInvitationEmail(String to, String inviteLink, String orgName) {
        log.info("╔════════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                    DEV EMAIL SERVICE - MOCK EMAIL                              ║");
        log.info("╠════════════════════════════════════════════════════════════════════════════════╣");
        log.info("║  To:      {}", to);
        log.info("║  Subject: You've been invited to join {}", orgName);
        log.info("║  Link:    {}", inviteLink);
        log.info("╠════════════════════════════════════════════════════════════════════════════════╣");
        log.info("║  NOTE: Run with SPRING_PROFILES_ACTIVE=prod to send real emails via SES       ║");
        log.info("╚════════════════════════════════════════════════════════════════════════════════╝");
    }
}
