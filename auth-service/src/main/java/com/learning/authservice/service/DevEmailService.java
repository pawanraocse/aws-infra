package com.learning.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Primary
@Profile("!prod")
@Slf4j
public class DevEmailService implements EmailService {

    @Override
    public void sendInvitationEmail(String to, String inviteLink, String orgName) {
        log.info("========================================================================================");
        log.info("DEV EMAIL SERVICE - MOCK EMAIL SENT");
        log.info("To: {}", to);
        log.info("Subject: You've been invited to join {}", orgName);
        log.info("Body: Click here to join: {}", inviteLink);
        log.info("========================================================================================");
    }
}
