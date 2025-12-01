package com.learning.authservice.service;

public interface EmailService {
    void sendInvitationEmail(String to, String inviteLink, String orgName);
}
