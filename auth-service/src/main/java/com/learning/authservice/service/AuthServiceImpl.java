package com.learning.authservice.service;

import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.dto.AuthRequestDto;
import com.learning.authservice.dto.AuthResponseDto;
import com.learning.authservice.dto.SignupRequestDto;
import com.learning.authservice.dto.UserInfoDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;

import java.util.Map;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private CognitoProperties cognitoProperties;
    @Autowired
    private HttpServletRequest request;
    @Autowired
    private HttpServletResponse response;

    private CognitoIdentityProviderClient cognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(cognitoProperties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoDto getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            log.info("operation=getCurrentUser, userId=anonymous, requestId={}, status=unauthenticated", request.getAttribute("X-Request-Id"));
            throw new RuntimeException("User not authenticated");
        }
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            log.warn("operation=getCurrentUser, userId=unknown, requestId={}, status=invalidPrincipal", request.getAttribute("X-Request-Id"));
            throw new RuntimeException("Invalid user principal");
        }
        String userId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = StringUtils.hasText(oidcUser.getFullName()) ? oidcUser.getFullName() : oidcUser.getPreferredUsername();
        log.info("operation=getCurrentUser, userId={}, requestId={}, status=success", userId, request.getAttribute("X-Request-Id"));
        return new UserInfoDto(userId, email, name);
    }

    @Override
    @Transactional
    public AuthResponseDto login(AuthRequestDto requestDto) {
        try (CognitoIdentityProviderClient client = cognitoClient()) {
            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .clientId(cognitoProperties.getClientId())
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(Map.of(
                            "USERNAME", requestDto.getEmail(),
                            "PASSWORD", requestDto.getPassword()
                    ))
                    .build();
            AdminInitiateAuthResponse response = client.adminInitiateAuth(authRequest);
            var result = response.authenticationResult();
            log.info("operation=login, userId={}, requestId={}, status=success", requestDto.getEmail(), request.getAttribute("X-Request-Id"));
            return new AuthResponseDto(
                    result.accessToken(),
                    result.refreshToken(),
                    result.tokenType(),
                    result.expiresIn() != null ? result.expiresIn().longValue() : null,
                    requestDto.getEmail(),
                    requestDto.getEmail()
            );
        } catch (CognitoIdentityProviderException e) {
            log.warn("operation=login, userId={}, requestId={}, status=failed, error={}", requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Login failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    @Transactional
    public AuthResponseDto signup(SignupRequestDto requestDto) {
        try (CognitoIdentityProviderClient client = cognitoClient()) {
            AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(requestDto.getEmail())
                    .userAttributes(
                            AttributeType.builder().name("email").value(requestDto.getEmail()).build(),
                            AttributeType.builder().name("name").value(requestDto.getName()).build()
                    )
                    .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                    .messageAction(MessageActionType.SUPPRESS)
                    .build();
            client.adminCreateUser(createUserRequest);
            // Set password (requires separate call)
            client.adminSetUserPassword(b -> b
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(requestDto.getEmail())
                    .password(requestDto.getPassword())
                    .permanent(true)
            );
            log.info("operation=signup, userId={}, requestId={}, status=success", requestDto.getEmail(), request.getAttribute("X-Request-Id"));
            // Optionally, auto-login after signup
            AuthRequestDto loginRequest = new AuthRequestDto();
            loginRequest.setEmail(requestDto.getEmail());
            loginRequest.setPassword(requestDto.getPassword());
            return login(loginRequest);
        } catch (CognitoIdentityProviderException e) {
            log.warn("operation=signup, userId={}, requestId={}, status=failed, error={}", requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Signup failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    @Transactional
    public void logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
            log.info("operation=logout, userId={}, requestId={}, status=success", auth.getName(), request.getAttribute("X-Request-Id"));
        } else {
            log.info("operation=logout, userId=anonymous, requestId={}, status=not_authenticated", request.getAttribute("X-Request-Id"));
        }
    }
}
