package com.learning.authservice.service;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.service.GroupRoleMappingService;
import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.dto.AuthRequestDto;
import com.learning.authservice.dto.AuthResponseDto;
import com.learning.authservice.dto.SignupRequestDto;
import com.learning.authservice.dto.SignupResponseDto;
import com.learning.authservice.dto.UserInfoDto;
import com.learning.authservice.exception.AuthLoginException;
import com.learning.authservice.exception.AuthSignupException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

        private final CognitoProperties cognitoProperties;
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final CognitoIdentityProviderClient cognitoClient;
        private final org.springframework.web.reactive.function.client.WebClient platformWebClient;
        private final UserRoleService userRoleService;
        private final GroupRoleMappingService groupRoleMappingService;

        @Override
        @Transactional(readOnly = true)
        public UserInfoDto getCurrentUser() {
                String userId = request.getHeader("X-User-Id");
                if (userId == null || userId.isBlank()) {
                        log.info("operation=getCurrentUser, userId=anonymous, requestId={}, status=unauthenticated",
                                        request.getAttribute("X-Request-Id"));
                        throw new RuntimeException("User not authenticated");
                }

                String email = request.getHeader("X-Email");
                String name = request.getHeader("X-Username");

                // Priority 1: Check group mappings from X-Groups header (SSO users)
                String groups = request.getHeader("X-Groups");
                String role = null;
                if (groups != null && !groups.isBlank()) {
                        java.util.List<String> groupList = java.util.Arrays.asList(groups.split(","));
                        role = groupRoleMappingService.resolveRoleFromGroups(groupList).orElse(null);
                        if (role != null) {
                                log.debug("Role from group mapping: userId={} groups={} role={}", userId, groups, role);
                        }
                }

                // Priority 2: Check user_roles table
                if (role == null) {
                        List<UserRole> userRoles = userRoleService.getUserRoles(userId);
                        role = userRoles.isEmpty() ? null : userRoles.get(0).getRoleId();
                }

                // Priority 3: Default to viewer
                if (role == null) {
                        role = "viewer";
                        log.debug("No role found for userId={}, defaulting to viewer", userId);
                }

                log.info("operation=getCurrentUser, userId={}, role={}, requestId={}, status=success", userId, role,
                                request.getAttribute("X-Request-Id"));
                return UserInfoDto.builder()
                                .userId(userId)
                                .email(email)
                                .name(name)
                                .role(role)
                                .build();
        }

        @Override
        @Transactional
        public AuthResponseDto login(AuthRequestDto requestDto) {
                try {
                        // Build auth parameters with SECRET_HASH if client secret is configured
                        Map<String, String> authParams = new HashMap<>();
                        authParams.put("USERNAME", requestDto.getEmail());
                        authParams.put("PASSWORD", requestDto.getPassword());

                        // Add SECRET_HASH if client secret is configured
                        String clientSecret = cognitoProperties.getClientSecret();
                        if (clientSecret != null && !clientSecret.isEmpty()) {
                                String secretHash = computeSecretHash(
                                                requestDto.getEmail(),
                                                cognitoProperties.getClientId(),
                                                clientSecret);
                                authParams.put("SECRET_HASH", secretHash);
                        }

                        AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                                        .userPoolId(cognitoProperties.getUserPoolId())
                                        .clientId(cognitoProperties.getClientId())
                                        .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                                        .authParameters(authParams)
                                        .build();
                        AdminInitiateAuthResponse cognitoResponse = cognitoClient.adminInitiateAuth(authRequest);
                        var result = cognitoResponse.authenticationResult();
                        log.info("operation=login status=success userId={} requestId={}", requestDto.getEmail(),
                                        request.getAttribute("X-Request-Id"));
                        return new AuthResponseDto(
                                        result.accessToken(),
                                        null,
                                        result.refreshToken(),
                                        result.tokenType(),
                                        result.expiresIn() != null ? result.expiresIn().longValue() : null,
                                        requestDto.getEmail(),
                                        requestDto.getEmail());
                } catch (NotAuthorizedException e) {
                        log.warn("operation=login status=failed code=INVALID_CREDENTIALS userId={} requestId={} error={}",
                                        requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.getMessage());
                        throw new AuthLoginException("INVALID_CREDENTIALS", "Invalid username or password", e);
                } catch (UserNotFoundException e) {
                        log.warn("operation=login status=failed code=USER_NOT_FOUND userId={} requestId={} error={}",
                                        requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.getMessage());
                        throw new AuthLoginException("USER_NOT_FOUND", "User not found", e);
                } catch (CognitoIdentityProviderException e) {
                        log.warn("operation=login status=failed code=LOGIN_FAILED userId={} requestId={} error={} awsCode={}",
                                        requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.getMessage(),
                                        e.awsErrorDetails().errorCode());
                        throw new AuthLoginException("LOGIN_FAILED",
                                        "Login failed: " + e.awsErrorDetails().errorMessage(), e);
                }
        }

        /**
         * Compute SECRET_HASH for Cognito authentication.
         * Required when the app client is configured with a client secret.
         */
        private String computeSecretHash(String username, String clientId, String clientSecret) {
                try {
                        String message = username + clientId;
                        Mac mac = Mac.getInstance("HmacSHA256");
                        SecretKeySpec secretKeySpec = new SecretKeySpec(
                                        clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                        mac.init(secretKeySpec);
                        byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
                        return Base64.getEncoder().encodeToString(rawHmac);
                } catch (Exception e) {
                        log.error("Failed to compute SECRET_HASH: {}", e.getMessage());
                        throw new RuntimeException("Failed to compute SECRET_HASH", e);
                }
        }

        @Override
        @Transactional
        public SignupResponseDto signup(SignupRequestDto requestDto) {
                try {
                        // Use signUp API instead of adminCreateUser to enable email verification
                        SignUpRequest signUpRequest = SignUpRequest.builder()
                                        .clientId(cognitoProperties.getClientId())
                                        .username(requestDto.getEmail())
                                        .password(requestDto.getPassword())
                                        .userAttributes(
                                                        AttributeType.builder()
                                                                        .name("email")
                                                                        .value(requestDto.getEmail())
                                                                        .build(),
                                                        AttributeType.builder()
                                                                        .name("name")
                                                                        .value(requestDto.getName())
                                                                        .build())
                                        .clientMetadata(java.util.Map.of(
                                                        "tenantId",
                                                        requestDto.getEmail().replace("@", "-").replace(".", "-"), // Simple
                                                                                                                   // tenant
                                                                                                                   // ID
                                                                                                                   // generation
                                                                                                                   // for
                                                                                                                   // personal
                                                                                                                   // users
                                                        "role", "admin"))
                                        .build();

                        SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);

                        log.info("operation=signup status=success userId={} userConfirmed={} requestId={}",
                                        requestDto.getEmail(),
                                        signUpResponse.userConfirmed(),
                                        request.getAttribute("X-Request-Id"));

                        // Return signup response with verification status
                        SignupResponseDto response = new SignupResponseDto();
                        response.setEmail(requestDto.getEmail());
                        response.setUserConfirmed(signUpResponse.userConfirmed());
                        response.setUserSub(signUpResponse.userSub());
                        response.setMessage(signUpResponse.userConfirmed()
                                        ? "Signup successful. You can now login."
                                        : "Signup successful. Please check your email to verify your account.");

                        return response;

                } catch (UsernameExistsException e) {
                        log.warn("operation=signup status=failed code=USER_EXISTS userId={} requestId={} error={}",
                                        requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.getMessage());
                        throw new AuthSignupException("USER_EXISTS", "User already exists", e);
                } catch (InvalidPasswordException e) {
                        log.warn("operation=signup status=failed code=INVALID_PASSWORD userId={} requestId={} error={}",
                                        requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.getMessage());
                        throw new AuthSignupException("INVALID_PASSWORD",
                                        "Password does not meet requirements", e);
                } catch (CognitoIdentityProviderException e) {
                        log.warn("operation=signup status=failed code=SIGNUP_FAILED userId={} requestId={} error={} awsCode={}",
                                        requestDto.getEmail(), request.getAttribute("X-Request-Id"), e.getMessage(),
                                        e.awsErrorDetails().errorCode());
                        throw new AuthSignupException("SIGNUP_FAILED",
                                        "Signup failed: " + e.awsErrorDetails().errorMessage(), e);
                }
        }

        @Override
        @Transactional
        public void logout() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null) {
                        new SecurityContextLogoutHandler().logout(request, response, auth);
                        log.info("operation=logout, userId={}, requestId={}, status=success", auth.getName(),
                                        request.getAttribute("X-Request-Id"));
                } else {
                        log.info("operation=logout, userId=anonymous, requestId={}, status=not_authenticated",
                                        request.getAttribute("X-Request-Id"));
                }
        }

        @Override
        @Transactional
        public void deleteAccount() {
                String userId = request.getHeader("X-User-Id");
                String tenantId = request.getHeader("X-Tenant-Id");
                String roles = request.getHeader("X-Authorities");

                if (userId == null || tenantId == null) {
                        throw new RuntimeException("Missing authentication headers");
                }

                log.info("operation=deleteAccount_init userId={} tenantId={} roles={}", userId, tenantId, roles);

                // If user is a Tenant Admin, delete the entire tenant (Personal or Org)
                if (roles != null && roles.contains("admin")) {
                        try {
                                log.info("operation=deleteAccount_tenant_deletion tenantId={}", tenantId);
                                platformWebClient.delete()
                                                .uri("/api/tenants/" + tenantId)
                                                .retrieve()
                                                .toBodilessEntity()
                                                .block();
                        } catch (Exception e) {
                                log.error("operation=deleteAccount_tenant_failed tenantId={} error={}", tenantId,
                                                e.getMessage());
                                // Proceed to delete user anyway? Or fail?
                                // If platform delete fails, we probably shouldn't leave the user in a weird
                                // state.
                                // But if we don't delete user, they can't retry easily if the error is
                                // persistent.
                                // For now, throw exception.
                                throw new RuntimeException("Failed to delete tenant resources: " + e.getMessage());
                        }
                }

                // Delete Cognito User
                try {
                        software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest deleteRequest = software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest
                                        .builder()
                                        .userPoolId(cognitoProperties.getUserPoolId())
                                        .username(userId) // OidcUser subject is the username/sub
                                        .build();

                        cognitoClient.adminDeleteUser(deleteRequest);
                        log.info("operation=deleteAccount_cognito_success userId={}", userId);
                } catch (Exception e) {
                        log.error("operation=deleteAccount_cognito_failed userId={} error={}", userId, e.getMessage());
                        throw new RuntimeException("Failed to delete user: " + e.getMessage());
                }
        }
}
